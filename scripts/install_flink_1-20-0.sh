#!/bin/bash

sudo apt update
sudo apt install openjdk-17-jdk -y
sudo update-alternatives --config java
cd /opt
sudo wget https://archive.apache.org/dist/flink/flink-1.20.0/flink-1.20.0-bin-scala_2.12.tgz
sudo tar -xvzf flink-1.20.0-bin-scala_2.12.tgz
sudo mv flink-1.20.0 flink
echo 'export PATH=$PATH:/opt/flink/bin' >> ~/.bashrc
source ~/.bashrc
sudo mkdir -p /opt/flink/log
sudo chown -R $USER:$USER /opt/flink/log
flink --version
cd /opt/flink
./bin/start-cluster.sh
./bin/flink run examples/streaming/WordCount.jar

