@echo off
set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"
java --enable-native-access=ALL-UNNAMED --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -Djava.library.path="%APP_DIR%libs" -cp "%APP_DIR%libs\*;%APP_DIR%GeoEditor.jar" com.GeoEditor
