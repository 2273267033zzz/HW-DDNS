
FROM openjdk:8-jdk-alpine

LABEL maintainer="your-email@example.com"

WORKDIR /opt/hwddns

RUN mkdir -p jar yaml log

COPY ./target/hwddns.jar /opt/hwddns/jar/hwddns.jar

COPY ./src/main/resources/application.yml /opt/hwddns/yaml/application.yml

CMD ["nohup","java","-jar","/opt/hwddns/jar/hwddns.jar","--spring.config.location=/opt/hwddns/yaml/application.yml"]
