#!/bin/sh

# Configuration
ANDROID_HOME="sdk"
APP_LABEL_NAME=FileReceiver
APP_COMPANY_NAME=kototuz
APP_PRODUCT_NAME=file_receiver
APP_KEYSTORE_PASS=$APP_PRODUCT_NAME

BUILD_TOOLS=$ANDROID_HOME/build-tools/29.0.3
SRC_PATH=./src/com/$APP_COMPANY_NAME/$APP_PRODUCT_NAME
PACKAGE_NAME=com.$APP_COMPANY_NAME.$APP_PRODUCT_NAME

mkdir -p build/dex assets

if [ "$1" = "keystore" ]; then
    keytool -genkeypair -validity 1000 -dname "CN=$APP_KEYSTORE_PASS,O=Android,C=ES" -keystore build/$APP_KEYSTORE_PASS.keystore -storepass $APP_KEYSTORE_PASS -keypass $APP_KEYSTORE_PASS -alias projectKey -keyalg RSA
    exit 0
fi

# stop on error and display each command as it gets executed. Optional step but helpful in catching where errors happen if they do.
set -xe

$BUILD_TOOLS/aapt package -f -m \
	-S ./res -J ./src -M ./AndroidManifest.xml \
	-I $ANDROID_HOME/platforms/android-29/android.jar

# Compile R.java
javac -verbose -source 1.8 -target 1.8 -d ./build/obj \
	-bootclasspath jre/lib/rt.jar \
	-classpath $ANDROID_HOME/platforms/android-29/android.jar:build/obj \
	-sourcepath ./src $SRC_PATH/R.java $SRC_PATH/MainActivity.java

$BUILD_TOOLS/dx --verbose --dex --output=build/dex/classes.dex ./build/obj

# Add resources and assets to APK
$BUILD_TOOLS/aapt package -f \
	-M ./AndroidManifest.xml -S ./res -A ./assets \
	-I $ANDROID_HOME/platforms/android-29/android.jar -F ./$APP_LABEL_NAME.apk ./build/dex

# Zipalign APK and sign
# NOTE: If you changed the storepass and keypass in the setup process, change them here too
$BUILD_TOOLS/zipalign -f 4 ./$APP_LABEL_NAME.apk app.final.apk
mv -f app.final.apk ./$APP_LABEL_NAME.apk

# Install apksigner with `sudo apt install apksigner`
apksigner sign  --ks ./build/$APP_KEYSTORE_PASS.keystore --out ./build/my-app-release.apk --ks-pass pass:$APP_KEYSTORE_PASS $APP_LABEL_NAME.apk
mv ./build/my-app-release.apk ./$APP_LABEL_NAME.apk

# Install to device or emulator
$ANDROID_HOME/platform-tools/adb install -r ./$APP_LABEL_NAME.apk
