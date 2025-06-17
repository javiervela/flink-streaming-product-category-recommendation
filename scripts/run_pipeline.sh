#!/bin/bash

cd ~/vm_shared/flink-streaming-product-category-recommendation/flink_pipeline

sudo /opt/flink/bin/flink run \
    ./target/scala-2.12/flink_recommendation_pipeline.jar \
    --job-name RecommendationJob \
    --detached
