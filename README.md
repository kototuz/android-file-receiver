# Android File Receiver

> [!WARNING]
> This software is created for my own use. Keep your expectations low

## Demo

https://github.com/user-attachments/assets/5a4396ff-aa93-4c09-b0b6-12abeacc190b

## Build

Install [FileReceiver.apk](FileReceiver.apk) on your android and build sender:

``` console
./build_sender.sh <your_phone_ip>
```

> [!NOTE]
> You can implement your own sender. It is just a simple tcp client.
> Files are sent by this format: `<file_name_len><file_name><file_content_size><file_content>`

## Usage

Run the app on android. Then you can send files by this way:

``` console
./build/file_sender <dir|file>...
```
