package com.web.wps.util.upload.localhost;

import com.web.wps.config.FileServerConfig;
import com.web.wps.propertie.LocalhostProperties;
import com.web.wps.util.file.FileType;
import com.web.wps.util.file.FileTypeJudge;
import com.web.wps.util.file.FileUtil;
import com.web.wps.util.upload.ResFileDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

/**
 * @author Administrator
 * @Description
 * @create 2021-06-10 15:06
 */
@Service
public class LocalhostUtil {

    @Autowired
    public LocalhostUtil(LocalhostProperties localhostProperties,FileTypeJudge fileTypeJudge,FileServerConfig fileServerConfig) {
        this.localhostProperties = localhostProperties;
        this.fileTypeJudge = fileTypeJudge;
        this.fileServerConfig = fileServerConfig;
    }
    private final FileTypeJudge fileTypeJudge;
    private final LocalhostProperties localhostProperties;
    private final FileServerConfig fileServerConfig;


    public ResFileDTO uploadMultipartFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        InputStream inputStream;
        ResFileDTO o = new ResFileDTO();
        String fileType;
        long fileSize = file.getSize();
        try {
            inputStream = file.getInputStream();
            FileType type = fileTypeJudge.getType(inputStream);

            if (type == null || "null".equals(type.toString()) ||
                    "XLS_DOC".equals(type.toString()) || "XLSX_DOCX".equals(type.toString()) ||
                    "WPSUSER".equals(type.toString()) || "WPS".equals(type.toString())) {
                fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            } else {
                fileType = type.toString().toLowerCase();
            }

        } catch (Exception e) {
            e.printStackTrace();
            //用户上传的文件类型为空，并且通过二进制流获取不到文件类型，因为二进制流只列举了常用的
            fileType = "";
        }

        try {
            o = this.uploadDetailInputStream(file.getInputStream(), fileName, fileType, fileSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return o;
    }

    public ResFileDTO uploadDetailInputStream(InputStream in, String fileName, String fileType, long fileSize) throws IOException {

        String uuidFileName = FileUtil.getFileUUIDName(fileName, fileType);

        String filePath = localhostProperties.getFileDir()+ File.separator + uuidFileName;
        File rootFile = new File(localhostProperties.getFileDir());
        if(!rootFile.exists()){
            rootFile.mkdirs();
        }
        File file = new File(filePath);
        if(!file.exists()){
            file.createNewFile();
        }

        this.writeFileInputStream(in,file);
        ResFileDTO o = new ResFileDTO();

        String fileUrl = fileServerConfig.toServerPath(filePath);
        
        o.setFileType(fileType);
        o.setFileName(fileName);
        o.setCFileName(uuidFileName);
        o.setFileUrl(fileUrl);
        o.setFileSize(fileSize);
        return o;
    }

    public void writeFileInputStream(InputStream in,File outFile){

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int length;
            while((length= in.read(buffer)) != -1){
                outputStream.write(buffer,0,length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(outputStream != null){
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
