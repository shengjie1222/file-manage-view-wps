package com.web.wps;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.imm.model.v20170906.CreateOfficeConversionTaskRequest;
import com.aliyuncs.imm.model.v20170906.CreateOfficeConversionTaskResponse;
import com.aliyuncs.imm.model.v20170906.GetOfficeConversionTaskRequest;
import com.aliyuncs.imm.model.v20170906.GetOfficeConversionTaskResponse;
import com.aliyuncs.profile.DefaultProfile;
import org.junit.Test;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

public class ApplicationTests {

    @Test
    public void test01() {
        UUID randomUUID = UUID.randomUUID();
        String uuid = randomUUID.toString().replace("-", "");
        System.out.println(uuid);
    }

    public static void main(String[] args) {
        // 参数
        String accessKey = "";
        String accessSecret = "";
        String endpoint = "";
        String process = "";
        String bucketName = "";
        String objectKey = "";
        // exp参数，小时。即文档时效
        URL url = getUrl(1, process, accessKey, accessSecret, endpoint, bucketName, objectKey);
        System.out.println(url.toString());
    }

    private static URL getUrl(Integer exp, String process, String accessKey, String accessSecret,
                              String endpoint, String bucketName, String objectKey) {
        OSSClient client = new OSSClient(endpoint, accessKey, accessSecret);
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
        getObjectRequest.setProcess(process);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectKey);
        request.setProcess(process);
        request.setExpiration(new Date(new Date().getTime() + (3600 * 1000 * exp)));
        return client.generatePresignedUrl(request);
    }

    /**
     * @param url        完整的url
     * @param pdfName    转换后的名称，不需要后缀
     * @param tgFileType 转换后的后缀，如pdf,doc,docx,xls,xlsx,ppt,pptx,jpeg,jpg,png等，不要加点 "."
     * @return 转换后的URL
     */
    public static String convertFile(String url, String pdfName, String tgFileType) throws ClientException, com.aliyuncs.exceptions.ClientException {
        // 参数
        String immRegionId = "";
        String imMmediaProjectName = "";
        String accessKey = "";
        String accessSecret = "";
        String bucketName = "";
        String diskName = "";
        String fileUrlPrefix = "";// 文件地址前缀，如 http://www.file.cn,后面接文件名，组成http://www.file.cn/a.docx

        String[] pathArr = url.split("/");
        String fileName;
        if (pathArr.length > 1) {
            fileName = pathArr[pathArr.length - 1];
        } else {
            fileName = url;
        }

        IAcsClient client = new DefaultAcsClient(DefaultProfile.getProfile(immRegionId, accessKey, accessSecret));

        // 创建文档转换异步请求任务
        CreateOfficeConversionTaskRequest req = new CreateOfficeConversionTaskRequest();

        // 构建返回的文件URL
        String fileUrl = fileUrlPrefix + "/" + diskName + "/" + pdfName + "." + tgFileType;

        // 设置项目智能煤体名称
        req.setProject(imMmediaProjectName);
        // 设置待转换对文件OSS路径
        String srcUri = "oss://" + bucketName + "/" + diskName + "/" + fileName;
        req.setSrcUri(srcUri);
        // 设置文件输出pdf格式
        req.setTgtType(tgFileType);
        // 设置文件输出vector向量格式，方便预览
//        req.setTgtType("vector");
        // 设置转换后的输出路径
        String tgtUri = "oss://" + bucketName + "/" + diskName + "/";
        req.setTgtUri(tgtUri);

        // 转换后的文件名
        req.setTgtFilePrefix(pdfName);
        // 转换后的文件后缀，可以不设置，当TgtType=pdf时候，后缀默认
        req.setTgtFileSuffix("." + tgFileType);

        // 设置调用方法,当请求类为CreateOfficeConversionTaskRequest，可以不用设置
        req.setActionName("CreateOfficeConversionTask");
        // 设置转换最大页，-1全部
        req.setEndPage(-1L);
        // 设置转换最大行
        req.setMaxSheetRow(-1L);
        // 设置转换最大列
        req.setMaxSheetCol(-1L);
        // 设置转换最大sheet数量
        req.setMaxSheetCount(-1L);
        // 表格文件转pdf时，将列全部输出在一页，默认为 false，只有设置 TgtType 为 pdf 时才会生效
        req.setFitToPagesWide(true);

        CreateOfficeConversionTaskResponse res = client.getAcsResponse(req);

        String taskId = res.getTaskId();
        // 获取文档转换任务结果，最多轮询 30 次
        // 每次轮询的间隔为 1 秒
        GetOfficeConversionTaskRequest getOfficeConversionTaskRequest = new GetOfficeConversionTaskRequest();
        getOfficeConversionTaskRequest.setProject(imMmediaProjectName);
        getOfficeConversionTaskRequest.setTaskId(taskId);
        int maxCount = 30;
        int count = 0;
        try {
            while (true) {
                Thread.sleep(1000); // 1 秒
                GetOfficeConversionTaskResponse getOfficeConversionTaskResponse = client.getAcsResponse(getOfficeConversionTaskRequest);
                if (!getOfficeConversionTaskResponse.getStatus().equals("Running")) {
                    // 输出文档转换任务执行结果
                    System.out.println("taskId:" + getOfficeConversionTaskResponse.getTaskId());
                    System.out.println("errorMsg:" + getOfficeConversionTaskResponse.getFailDetail().getCode());
                    if (!"NoError".equalsIgnoreCase(getOfficeConversionTaskResponse.getFailDetail().getCode())) {
                        fileUrl = null;
                    }
                    System.out.println("------Done------");
                    break;
                }
                count = count + 1;
                if (count >= maxCount) {
                    System.out.println("OfficeConversion Timeout for 30 seconds");
                    break;
                }
                System.out.println("Task is still running...");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return fileUrl;
    }

}
