# ServerAgent

# 此工具为热更java服务器工具，可以更新运行中jar里面的java逻辑。兼容windows和Linux运行。
## ServerAgent需要打成一个jar给Agent工程使用。
## 使用方式
  * 修改Agent依赖 ServerAgent模块为ServerAgent.jar  
  * 需要热更的工程将Agent引入  
  * 需要热更的工程需要在根目录创建`anget/agentJava`和`agent/agentClass`两个目录
  * 程序初始化需要调用`Agent.initialize()`
  * 将需要热更的java文件放在`anget/agentJava`目录下，然后调用方法`Agent.agent(fileName, isDirectory)`


## 使用限制
  * 只能修改方法块逻辑
  * 不能修改变量、不能改变方法

#### 使用唯一标准：在ide下能够热编译成功就表示能够热更新  
