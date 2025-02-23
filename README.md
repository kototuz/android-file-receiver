# Android File Receiver

> [!WARNING]
> This software is created for my own use. Keep your expectations low

## Demo

https://github.com/user-attachments/assets/bfdc229e-a1b7-47b6-8ac4-644846808d41

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
