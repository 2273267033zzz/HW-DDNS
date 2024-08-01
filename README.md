# HW-DDNS

#### 介绍

基于华为云dns做的java程序

使用方法：

一、你自己打包docker镜像或使用我提供的docker镜像

#打包镜像，如果你使用我提供的docker镜像，这步可以跳过

docker build -t hwddns .

#如果你使用我提供的docker镜像,需要你自己导入镜像

#docker load < hwddns.tar

#创建并运行容器

docker run --name hwddns --restart=always -d hwddns

#如果需要挂载yaml文件

#docker run --name hwddns --restart=always -v /你自己存放yaml的目录:/opt/hwddns/yaml -d hwddns



二、手动编译jar包，将jar包上传到服务器并运行

在application.yml文件里配置好华为云的信息，将jar包和配置文件上传到服务器，将jar包设为系统服务

编辑shell脚本:

#!/bin/sh
nohup java -jar hwddns.jar >> hw-ddns.log &

#如果你用的是我提供的jar包，你需要将你自己的yaml文件上传到jar包所在目录里

#nohup java -jar hwddns.jar --spring.config.location=application.yml  >> hw-ddns.log &

echo $! > ${execPath}/tpid

echo "hwddns已启动"

