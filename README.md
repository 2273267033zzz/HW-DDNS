# HW-DDNS

#### 介绍

基于华为云dns做的java程序

使用方法：
解压打包好的压缩文件，在application.yml文件里配置好华为云的信息，将jar包和配置文件上传到服务器，将jar包设为系统服务

编辑shell脚本:

#!/bin/sh

nohup java -jar hwddns.jar --spring.config.location=application.yml  >> hw-ddns.log &
echo $! > ${execPath}/tpid
echo "hwddns已启动"
