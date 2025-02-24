package com.kototuz.file_receiver;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.os.Environment;
import android.content.Intent;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;
import android.widget.Button;
import android.view.View;

import java.math.BigInteger;
import java.nio.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private TextView stateText;
    private Button selectFolderButton;

    private static final String TAG = "NetTesting";
    private static final int PORT = 6969;
    private static final int PICK_DIR = 69;
    private static final int FIRST_PICK_DIR = 70;
    private static final int READ_SIZE = 1024;

    private Uri outputTreeUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_layout);

        stateText = findViewById(R.id.state_text);
        stateText.setTextSize(24.0f);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, FIRST_PICK_DIR);

        selectFolderButton = findViewById(R.id.select_folder_button);
        selectFolderButton.setTextSize(24.0f);
        selectFolderButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, PICK_DIR);
            }
        });
    }

    void listenForConnection() {
        Handler handler = new Handler(Looper.getMainLooper());
        ContentResolver contentResolver = getContentResolver();
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            byte[] contentBuffer = new byte[READ_SIZE];
            byte[] fileSizeBytes = new byte[8];
            byte[] fileNameLenBytes = new byte[8];
            byte[] fileNameBytes = new byte[4096];
            while (true) {
                handler.post(new Runnable(){public void run(){ stateText.setText("Waiting for connection"); }});
                try {
                    Socket clientSocket = serverSocket.accept();
                    handler.post(new Runnable(){public void run(){ selectFolderButton.setVisibility(View.INVISIBLE); }});

                    BufferedInputStream input = new BufferedInputStream(clientSocket.getInputStream());
                    while (input.read(fileNameLenBytes, 0, 8) == 8) {
                        int fileNameLen = ByteBuffer.wrap(fileNameLenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (input.read(fileNameBytes, 0, fileNameLen) != fileNameLen) {
                            throw new RuntimeException("Not all file name bytes arrived");
                        }

                        String fileName = new String(fileNameBytes, 0, fileNameLen);

                        if (input.read(fileSizeBytes, 0, 8) != 8) {
                            throw new RuntimeException("Not all file size bytes arrived");
                        }

                        int fileSize = ByteBuffer.wrap(fileSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                        int readedBytes = 0;
                        Uri docUri;
                        synchronized (outputTreeUri) {
                            docUri = DocumentsContract.createDocument(contentResolver, outputTreeUri, "plain/text", fileName);
                        }
                        OutputStream output = contentResolver.openOutputStream(docUri);
                        if (output != null) {
                            while (true) {
                                int diff = fileSize - readedBytes;
                                if (diff == 0) {
                                    break;
                                }

                                int res;
                                if (diff < READ_SIZE) {
                                    res = input.read(contentBuffer, 0, diff);
                                } else {
                                    res = input.read(contentBuffer);
                                }

                                if (res == -1) {
                                    handler.post(new Runnable(){public void run(){ Toast.makeText(MainActivity.this, "Not all file content arrived", Toast.LENGTH_LONG).show(); }});
                                    break;
                                }

                                output.write(contentBuffer, 0, res);
                                readedBytes += res;
                                final int readed = readedBytes;
                                handler.post(new Runnable(){public void run(){ stateText.setText(fileName+"\n("+readed+"/"+fileSize+")"); }});
                            }

                            output.close();
                        }
                    }

                    handler.post(new Runnable(){public void run(){ Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_SHORT).show(); }});

                    clientSocket.close();

                    handler.post(new Runnable(){public void run(){ selectFolderButton.setVisibility(View.VISIBLE); }});
                } catch (Exception e) {
                    handler.post(new Runnable(){public void run(){ Toast.makeText(MainActivity.this, "Failed to start waiting for connection", Toast.LENGTH_LONG); }});
                    Log.e(TAG, e.getMessage());
                }
            }
        } catch (Exception e) {
            handler.post(new Runnable(){public void run(){ Toast.makeText(MainActivity.this, "Failed to start server", Toast.LENGTH_LONG); }});
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (intent == null) {
            Log.e(TAG, "Intent is null");
            return;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            Log.e(TAG, "Data is null");
            return;
        }

        try {
            if (requestCode == PICK_DIR) {
                String documentId = DocumentsContract.getTreeDocumentId(uri);
                synchronized (outputTreeUri) {
                    outputTreeUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);
                }
            } else if (requestCode == FIRST_PICK_DIR) {
                String documentId = DocumentsContract.getTreeDocumentId(uri);
                outputTreeUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);
                Executors.newSingleThreadExecutor().execute(new Runnable(){public void run(){ listenForConnection(); }});
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
