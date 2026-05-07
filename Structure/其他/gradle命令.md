快速检测kotlin语法（只编译不资源处理（更快）：）
.\gradlew.bat :app:compileAppMaxDebugKotlin -x :app:processAppMaxDebugResources
只编译该渠道 Debug 版的 Kotlin 代码，不打包、不处理资源
.\gradlew.bat :app:compileAppMaxDebugKotlin


打包Debug版本
.\gradlew.bat :app:assembleDebug
./gradlew assembleAppMaxDebug 打包MaxDebug版本

打包Release版本
.\gradlew.bat :app:assembleRelease
./gradlew assembleAppMaxRelease 打包MaxRelease版本

查看所有可用编译任务：
.\gradlew.bat tasks
.\gradlew.bat :app:tasks --all | findstr compile.*Kotlin