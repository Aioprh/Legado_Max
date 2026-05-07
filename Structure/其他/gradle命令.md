快速检测语法
.\gradlew.bat :app:compileAppMaxDebugKotlin -x :app:processAppMaxDebugResources



打包Debug版本
.\gradlew.bat :app:assembleDebug
./gradlew assembleAppMaxDebug 打包MaxDebug版本

打包Release版本
.\gradlew.bat :app:assembleRelease
./gradlew assembleAppMaxRelease 打包MaxRelease版本

