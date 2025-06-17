package es.uimp.bigdata.flink

import java.io.FileInputStream

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{FlatMapFunction, MapFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.{
  KafkaRecordSerializationSchema,
  KafkaSink
}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.util.Collector

import org.jpmml.evaluator.{LoadingModelEvaluatorBuilder, ModelEvaluator}

import org.json4s._
import org.json4s.jackson.Serialization.write
import org.json4s.jackson.JsonMethods.parse

import io.github.cdimascio.dotenv.Dotenv

import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

object RecommendationJob {

  implicit val formats: Formats = DefaultFormats

  // Load settings from .env
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  private val brokers = dotenv.get("BROKERS")
  private val pmmlPath = dotenv.get("PMML_PATH")
  private val token = dotenv.get("TOKEN")
  private val demoTopic =
    Option(dotenv.get("DEMO_TOPIC")).getOrElse("topic_demographic")
  private val histTopic =
    Option(dotenv.get("HIST_TOPIC")).getOrElse("topic_historic")
  private val outTopic =
    Option(dotenv.get("OUT_TOPIC")).getOrElse("topic_student_prediction")

  // Domain case classes
  case class Demographic(uuid: Long, age: Int, man: Int, woman: Int)
  case class Historic(uuid: Long, products: Map[String, Int])
  case class User(uuid: Long, features: Map[String, Any])
  case class Prediction(uuid: Long, value: Int, token: String)

  // Demographic JSON parser
  class DemographicParser extends FlatMapFunction[String, (Long, Demographic)] {
    override def flatMap(
        json: String,
        out: Collector[(Long, Demographic)]
    ): Unit = {
      val demo = parse(json).extract[Demographic]
      out.collect(demo.uuid -> demo)
    }
  }

  // Historic JSON parser
  class HistoricParser extends FlatMapFunction[String, (Long, Historic)] {
    override def flatMap(
        json: String,
        out: Collector[(Long, Historic)]
    ): Unit = {
      val hist = parse(json).extract[Historic]
      out.collect(hist.uuid -> hist)
    }
  }

  // Function to join demographic and historic data
  class JoinFunction
      extends KeyedCoProcessFunction[
        Long,
        (Long, Demographic),
        (Long, Historic),
        User
      ] {

    private var demoState: ValueState[Demographic] = _
    private var histState: ValueState[Historic] = _

    // Initialize state descriptors
    override def open(cfg: Configuration): Unit = {
      demoState = getRuntimeContext.getState(
        new ValueStateDescriptor[Demographic]("demo", classOf[Demographic])
      )
      histState = getRuntimeContext.getState(
        new ValueStateDescriptor[Historic]("hist", classOf[Historic])
      )
    }

    // Create user from demographic and historic data
    private def createUser(
        demo: Demographic,
        hist: Historic
    ): User = {
      val features = Map(
        "age" -> demo.age,
        "man" -> demo.man,
        "woman" -> demo.woman
      ) ++ hist.products
      User(demo.uuid, features)
    }

    // Process demographic records and join them with historic data
    override def processElement1(
        demoRec: (Long, Demographic),
        ctx: KeyedCoProcessFunction[
          Long,
          (Long, Demographic),
          (Long, Historic),
          User
        ]#Context,
        out: Collector[User]
    ): Unit = {
      val demo = demoRec._2
      demoState.update(demo)

      Option(histState.value()) match {
        case Some(hist) =>
          out.collect(createUser(demo, hist))
          demoState.clear()
          histState.clear()
        case None =>
      }
    }

    // Process historic records and join them with demographic data
    override def processElement2(
        histRec: (Long, Historic),
        ctx: KeyedCoProcessFunction[
          Long,
          (Long, Demographic),
          (Long, Historic),
          User
        ]#Context,
        out: Collector[User]
    ): Unit = {
      val hist = histRec._2
      histState.update(hist)

      Option(demoState.value()) match {
        case Some(demo) =>
          out.collect(createUser(demo, hist))
          demoState.clear()
          histState.clear()
        case None =>
      }
    }
  }

  // PMML model predictor
  class PMMLPredictor(pmmlPath: String, token: String)
      extends MapFunction[User, String] {

    @transient private lazy val modelEvaluator: ModelEvaluator[_] = {
      val e = new LoadingModelEvaluatorBuilder()
        .load(new FileInputStream(pmmlPath))
        .build()
      e.verify()
      e
    }

    override def map(user: User): String = {
      val inputMap = new java.util.LinkedHashMap[String, Any]()

      // Add input fields
      modelEvaluator.getInputFields.asScala.foreach { field =>
        val fieldName = field.getName.toString
        val fieldValue = user.features.getOrElse(fieldName, "")
        inputMap.put(fieldName, fieldValue)
      }

      // Evaluate the model
      val result = modelEvaluator.evaluate(inputMap)

      // Get predicted value
      val predictedValue = result.get("predicted_label") match {
        case null => 0
        case value =>
          Try(value.toString.toInt).getOrElse(0)
      }
      
      // Output prediction
      write(Prediction(user.uuid, predictedValue, token))
    }
  }

  def main(args: Array[String]): Unit = {
    // Create the Flink execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(60000L)

    // Configure Demographic Kafka source
    val demoSource = KafkaSource
      .builder[String]()
      .setBootstrapServers(brokers)
      .setTopics(demoTopic)
      .setGroupId("demo-group")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new SimpleStringSchema())
      .build()

    // Configure Historic Kafka source
    val histSource = KafkaSource
      .builder[String]()
      .setBootstrapServers(brokers)
      .setTopics(histTopic)
      .setGroupId("historic-group")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new SimpleStringSchema())
      .build()

    // Create Demographic data stream
    val demoStream = env
      .fromSource(demoSource, WatermarkStrategy.noWatermarks(), "demo-source")
      .flatMap(new DemographicParser)
      .keyBy(new KeySelector[(Long, Demographic), Long] {
        override def getKey(value: (Long, Demographic)): Long = value._1
      })

    // Create Historic data stream
    val histStream = env
      .fromSource(histSource, WatermarkStrategy.noWatermarks(), "hist-source")
      .flatMap(new HistoricParser)
      .keyBy(new KeySelector[(Long, Historic), Long] {
        override def getKey(value: (Long, Historic)): Long = value._1
      })

    // Configure Output Kafka sink
    val kafkaSink = KafkaSink
      .builder[String]()
      .setBootstrapServers(brokers)
      .setDeliverGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .setRecordSerializer(
        KafkaRecordSerializationSchema
          .builder()
          .setTopic(outTopic)
          .setValueSerializationSchema(new SimpleStringSchema())
          .build()
      )
      .build()

    // Build the pipeline
    demoStream
      .connect(histStream)
      .process(new JoinFunction)
      .map(new PMMLPredictor(pmmlPath, token))
      .sinkTo(kafkaSink)

    // Execute the Flink job
    env.execute("Flink Recommendation Pipeline")
  }
}
