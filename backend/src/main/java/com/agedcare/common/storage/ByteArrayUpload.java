package com.agedcare.common.storage;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
final class ByteArrayUpload implements MultipartFile {
    private final byte[] bytes;
    private final String name;
    ByteArrayUpload(byte[] bytes,String name){this.bytes=bytes;this.name=name;}
    public String getName(){return "file";}
    public String getOriginalFilename(){return name;}
    public String getContentType(){return "application/octet-stream";}
    public boolean isEmpty(){return bytes.length==0;}
    public long getSize(){return bytes.length;}
    public byte[] getBytes(){return bytes.clone();}
    public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
    public void transferTo(File destination)throws IOException{Files.write(destination.toPath(),bytes);}
}
