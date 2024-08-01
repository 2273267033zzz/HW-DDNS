#打包镜像
#docker build -t hwddns .
#运行容器
#docker run --name hwddns --restart=always -d hwddns
#如果需要挂载yaml文件
#docker run --name hwddns --restart=always -v /你自己存放yaml的目录:/opt/hwddns/yaml -d hwddns

FROM openjdk:8-jdk-alpine

LABEL maintainer="your-email@example.com"

WORKDIR /opt/hwddns

RUN mkdir -p jar yaml log

COPY ./target/hwddns.jar /opt/hwddns/jar/hwddns.jar

COPY ./src/main/resources/application.yml /opt/hwddns/yaml/application.yml

CMD ["nohup","java","-jar","/opt/hwddns/jar/hwddns.jar","--spring.config.location=/opt/hwddns/yaml/application.yml"]
