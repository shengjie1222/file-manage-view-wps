package com.web.wps.logic.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.web.wps.base.BaseRepository;
import com.web.wps.base.BaseService;
import com.web.wps.config.Context;
import com.web.wps.logic.dto.*;
import com.web.wps.logic.entity.*;
import com.web.wps.logic.repository.FileRepository;
import com.web.wps.propertie.*;
import com.web.wps.util.*;
import com.web.wps.util.file.FileUtil;
import com.web.wps.util.upload.ResFileDTO;
import com.web.wps.util.upload.UploadFileLocation;
import com.web.wps.util.upload.localhost.LocalhostUtil;
import com.web.wps.util.upload.oss.OSSUtil;
import com.web.wps.util.upload.qn.QNUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Slf4j
public class FileService extends BaseService<FileEntity, String> {

    private final WpsUtil wpsUtil;
    private final WpsProperties wpsProperties;
    private final OSSUtil ossUtil;
    private final UserAclService userAclService;
    private final WatermarkService watermarkService;
    private final UserService userService;
    private final FileVersionService fileVersionService;
    private final RedirectProperties redirect;
    private final QNUtil qnUtil;
    private final LocalhostUtil localhostUtil;
    private final UploadProperties uploadProperties;

    @Autowired
    public FileService(WpsUtil wpsUtil, WpsProperties wpsProperties, OSSUtil ossUtil,
                       UserAclService userAclService, WatermarkService watermarkService,
                       UserService userService, FileVersionService fileVersionService,
                       RedirectProperties redirect, QNUtil qnUtil,LocalhostUtil localhostUtil, UploadProperties uploadProperties) {
        this.wpsUtil = wpsUtil;
        this.wpsProperties = wpsProperties;
        this.ossUtil = ossUtil;
        this.userAclService = userAclService;
        this.watermarkService = watermarkService;
        this.userService = userService;
        this.fileVersionService = fileVersionService;
        this.redirect = redirect;
        this.qnUtil = qnUtil;
        this.localhostUtil = localhostUtil;
        this.uploadProperties = uploadProperties;
    }

    @Autowired
    private ConvertProperties convertProperties;
    @Autowired
    private ServerProperties serverProperties;

    @Override
    @SuppressWarnings("unchecked")
    @Resource(type = FileRepository.class)
    protected void setDao(BaseRepository baseRepository) {
        this.baseRepository = baseRepository;
    }

    public FileRepository getRepository() {
        return (FileRepository) this.baseRepository;
    }

    /**
     * 获取预览用URL
     *
     * @param fileUrl    文件url
     * @param checkToken 是否校验token
     */
    public Token getViewUrl(String fileUrl, boolean checkToken) {
        Token t = new Token();

        String fileType = FileUtil.getFileTypeByPath(fileUrl);
        // fileId使用uuid保证出现同样的文件而是最新文件
        UUID randomUUID = UUID.randomUUID();
        String uuid = randomUUID.toString().replace("-", "");
        String userId = Context.getUserId();

        Map<String, String> values = new HashMap<String, String>() {
            {
                put("_w_appid", wpsProperties.getAppid());
                if (checkToken) {
                    put("_w_tokentype", "1");
                }
                put(redirect.getKey(), redirect.getValue());
                put("_w_userid", "-1");
                put("_w_filepath", fileUrl);
                put("_w_filetype", "web");
            }
        };

        String wpsUrl = wpsUtil.getWpsUrl(values, fileType, uuid);

        t.setToken(uuid);
        t.setExpires_in(600);
        t.setWpsUrl(wpsUrl);

        return t;
    }

    /**
     * 获取预览用URL
     *
     * @param fileId     文件id
     * @param userId     用户id
     * @param checkToken 是否校验token
     */
    public Token getViewUrl(String fileId, String userId, boolean checkToken) {
        FileEntity fileEntity = this.findOne(fileId);
        if (fileEntity != null) {
            Token t = new Token();
            String fileName = fileEntity.getName();
            String fileType = FileUtil.getFileTypeByName(fileName);

            UUID randomUUID = UUID.randomUUID();
            String uuid = randomUUID.toString().replace("-", "");

            Map<String, String> values = new HashMap<String, String>() {
                {
                    put("_w_appid", wpsProperties.getAppid());
                    if (checkToken) {
                        put("_w_tokentype", "1");
                    }
                    put(redirect.getKey(), redirect.getValue());
                    put("_w_filepath", fileName);
                    put("_w_userid", userId);
                    put("_w_filetype", "db");
                }
            };

            String wpsUrl = wpsUtil.getWpsUrl(values, fileType, fileEntity.getId());

            t.setToken(uuid);
            t.setExpires_in(600);
            t.setWpsUrl(wpsUrl);

            return t;
        }
        return null;
    }

    /**
     * 获取预览用URL
     *
     * @param filePath 文件路径
     * @param userId   用户id
     * @param type     请求预览文件类型
     */
    public Map<String, Object> getFileInfo(String userId, String filePath, String type) {
        if ("web".equalsIgnoreCase(type)) {
            return getWebFileInfo(filePath);
        } else if ("db".equalsIgnoreCase(type)) {
            return getDbFileInfo(userId);
        }
        return null;
    }

    /**
     * 获取文件元数据
     *
     * @param filePath 文件路径
     */
    private Map<String, Object> getWebFileInfo(String filePath) {
        logger.info("_w_filepath:{}", filePath);

        // 构建默认user信息
        UserDTO wpsUser = new UserDTO(
                "-1", "我", "read", "https://zmfiletest.oss-cn-hangzhou.aliyuncs.com/user0.png"
        );

        int fileSize = FileUtil.getFileSize(filePath);

        // 构建文件
        FileDTO file = new FileDTO(
                Context.getFileId(), FileUtil.getFileName(filePath),
                1, fileSize, "-1", new Date().getTime(), filePath,
                // 默认设置为无水印，只读权限
                new UserAclBO(), new WatermarkBO()
        );

        return new HashMap<String, Object>() {
            {
                put("file", file);
                put("user", wpsUser);
            }
        };
    }

    /**
     * 获取文件元数据
     *
     * @param userId 用户id
     */
    private Map<String, Object> getDbFileInfo(String userId) {
        String fileId = Context.getFileId();

        // 获取文件信息
        FileEntity fileEntity = this.findOne(fileId);

        // 初始化文件读写权限为read
        String permission;

        // 增加用户权限
        UserAclEntity userAclEntity = userAclService.getRepository().findFirstByFileIdAndUserId(fileId, userId);
        UserAclBO userAcl = new UserAclBO();
        if (userAclEntity != null) {
            BeanUtils.copyProperties(userAclEntity, userAcl);
            permission = userAclEntity.getPermission();
        } else {
            userAcl.setHistory(1);
            userAcl.setRename(1);
            permission = "write";
        }

        // 增加水印
        WatermarkEntity watermarkEntity = watermarkService.getRepository().findFirstByFileId(fileId);
        WatermarkBO watermark = new WatermarkBO();
        if (watermarkEntity != null) {
            BeanUtils.copyProperties(watermarkEntity, watermark);
        }

        //获取user
        UserEntity wpsUser = userService.findOne(userId);
        UserDTO user = new UserDTO();
        if (wpsUser != null) {
            BeanUtils.copyProperties(wpsUser, user);
            user.setPermission(permission);
        }

        // 构建fileInfo
        FileDTO file = new FileDTO();
        BeanUtils.copyProperties(fileEntity, file);
        file.setUser_acl(userAcl);
        file.setWatermark(watermark);

        return new HashMap<String, Object>() {
            {
                put("file", file);
                put("user", user);
            }
        };
    }

    /**
     * 文件重命名
     *
     * @param fileName 文件名
     * @param userId   用户id
     */
    public void fileRename(String fileName, String userId) {
        String fileId = Context.getFileId();
        FileEntity file = this.findOne(fileId);
        if (file != null) {
            file.setName(fileName);
            file.setModifier(userId);
            Date date = new Date();
            file.setModify_time(date.getTime());
            this.update(file);
        }
    }

    /**
     * 新建文件
     *
     * @param file   文件
     * @param userId 用户id
     */
    public Map<String, Object> fileNew(MultipartFile file, String userId) {
        ResFileDTO resFileDTO;
        if (uploadProperties.getFileLocation().equalsIgnoreCase(UploadFileLocation.QN)) {
            resFileDTO = qnUtil.uploadMultipartFile(file);
        } else {
            resFileDTO = ossUtil.uploadMultipartFile(file);
        }
        String fileName = resFileDTO.getFileName();
        String fileUrl = resFileDTO.getFileUrl();
        int fileSize = (int) file.getSize();
        Date date = new Date();
        long dataTime = date.getTime();
        // 保存文件
        FileEntity f = new FileEntity(fileName, 1, fileSize, userId, userId, dataTime, dataTime, fileUrl);
        this.save(f);

        // 处理权限
        userAclService.saveUserFileAcl(userId, f.getId());

        // 处理水印
        watermarkService.saveWatermark(f.getId());

        // 处理返回
        Map<String, Object> map = new HashMap<>();
        map.put("redirect_url", this.getViewUrl(f.getId(), userId, false).getWpsUrl());
        map.put("user_id", userId);
        return map;
    }

    /**
     * 查询文件历史记录
     */
    public Map<String, Object> fileHistory(FileReqDTO req) {
        List<FileHisDTO> result = new ArrayList<>(1);
        if (req.getId() != null) {
            // 目前先实现获取所有的历史记录
            List<FileVersionEntity> versionList =
                    fileVersionService.getRepository().findByFileIdOrderByVersionDesc(req.getId());
            if (versionList != null && versionList.size() > 0) {
                Set<String> userIdSet = new HashSet<>();
                for (FileVersionEntity fileVersion : versionList) {
                    userIdSet.add(fileVersion.getModifier());
                    userIdSet.add(fileVersion.getCreator());
                }
                List<String> userIdList = new ArrayList<>(userIdSet);
                // 获取所有关联的人
                List<UserEntity> userList = userService.getRepository().findByIdIn(userIdList);

                if (userList != null && userList.size() > 0) {
                    for (FileVersionEntity fileVersion : versionList) {
                        FileHisDTO fileHis = new FileHisDTO();
                        BeanUtils.copyProperties(fileVersion, fileHis);
                        fileHis.setId(fileVersion.getFileId());
                        UserDTO creator = new UserDTO();
                        UserDTO modifier = new UserDTO();
                        for (UserEntity user : userList) {
                            if (user.getId().equals(fileVersion.getCreator())) {
                                BeanUtils.copyProperties(user, creator);
                            }
                            if (user.getId().equals(fileVersion.getModifier())) {
                                BeanUtils.copyProperties(user, modifier);
                            }
                        }
                        fileHis.setModifier(modifier);
                        fileHis.setCreator(creator);
                        result.add(fileHis);
                    }
                }
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("histories", result);
        return map;
    }

    /**
     * 保存文件
     *
     * @param mFile  文件
     * @param userId 用户id
     */
    public Map<String, Object> fileSave(MultipartFile mFile, String userId) {
        Date date = new Date();
        // 上传

        ResFileDTO resFileDTO;
        if (uploadProperties.getFileLocation().equalsIgnoreCase(UploadFileLocation.QN)) {
            resFileDTO = qnUtil.uploadMultipartFile(mFile);
        } else if(uploadProperties.getFileLocation().equalsIgnoreCase(UploadFileLocation.OSS)) {
            resFileDTO = ossUtil.uploadMultipartFile(mFile);
        } else {
            resFileDTO = localhostUtil.uploadMultipartFile(mFile);
        }
        int size = (int) resFileDTO.getFileSize();

        String fileId = Context.getFileId();
        FileEntity file = this.findOne(fileId);
        FileDTO fileInfo = new FileDTO();

        String oldFileUrl = file.getDownload_url();

        // 更新当前版本
        file.setVersion(file.getVersion() + 1);
        file.setDownload_url(resFileDTO.getFileUrl());
        file.setModifier(userId);
        file.setModify_time(date.getTime());
        file.setSize(size);
        this.update(file);

        // 保存历史版本
        FileVersionEntity fileVersion = new FileVersionEntity();
        BeanUtils.copyProperties(file, fileVersion);
        fileVersion.setFileId(fileId);
        fileVersion.setVersion(file.getVersion() - 1);
        fileVersion.setDownload_url(oldFileUrl);
        fileVersion.setSize(size);
        fileVersionService.save(fileVersion);

        // 返回当前版本信息
        BeanUtils.copyProperties(file, fileInfo);

        Map<String, Object> map = new HashMap<>();
        map.put("file", fileInfo);
        return map;
    }

    /**
     * 查询文件版本
     *
     * @param version 版本号
     */
    public Map<String, Object> fileVersion(int version) {
        FileDTO fileInfo = new FileDTO();
        String fileId = Context.getFileId();
        FileVersionEntity fileVersion =
                fileVersionService.getRepository().findByFileIdAndVersion(fileId, version);
        if (fileVersion != null) {
            BeanUtils.copyProperties(fileVersion, fileInfo);
            fileInfo.setId(fileVersion.getFileId());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("file", fileInfo);
        return map;
    }

    /**
     * 获取文件列表
     */
    public List<FileListDTO> getFileList() {
        return this.getRepository().findAllFile();
    }

    /**
     * 获取文件列表--分页
     */
    public Page<FileListDTO> getFileListByPage(com.web.wps.base.Page page) {
        PageRequest pages = new PageRequest(page.getPage() - 1, page.getSize());
        return this.getRepository().getAllFileByPage(pages);
    }

    /**
     * 删除文件
     *
     * @param id 文件id
     */
    public int delFile(String id) {
        FileEntity file = this.findOne(id);
        if (file != null) {
            if ("Y".equalsIgnoreCase(file.getCanDelete())) {
                // del
                this.getRepository().delFile(id);
                return 1;
            } else {
                return 0;
            }
        } else {
            return -1;
        }
    }

    /**
     * 上传文件
     *
     * @param file 文件
     */
    public void uploadFile(MultipartFile file) {
        String uploadUserId = "3";
        ResFileDTO resFileDTO;
        if (uploadProperties.getFileLocation().equalsIgnoreCase(UploadFileLocation.QN)) {
            resFileDTO = qnUtil.uploadMultipartFile(file);
        } else if(uploadProperties.getFileLocation().equalsIgnoreCase(UploadFileLocation.OSS)) {
            resFileDTO = ossUtil.uploadMultipartFile(file);
        } else {
            resFileDTO = localhostUtil.uploadMultipartFile(file);
        }
        // 上传成功后，处理数据库记录值
        Date date = new Date();
        long dataTime = date.getTime();
        // 保存文件
        FileEntity f = new FileEntity(resFileDTO.getFileName(), 1, ((int) resFileDTO.getFileSize()),
                uploadUserId, uploadUserId, dataTime, dataTime, resFileDTO.getFileUrl());
        this.save(f);

        // 处理权限
        userAclService.saveUserFileAcl(uploadUserId, f.getId());

        // 处理水印
        watermarkService.saveWatermark(f.getId());
    }

    /**
     * 创建临时文件
     */
    public String createTemplateFile(String template) {
        boolean typeTrue = FileUtil.checkCode(template);
        if (typeTrue) {
            return wpsUtil.getTemplateWpsUrl(template, "3");
        }
        return "";
    }

    /**
     * 文件原格式	转换后格式
     * word			pdf、png
     * excel		pdf、png
     * ppt			pdf
     * pdf			word、ppt、excel
     *
     * @param srcUri     文件url
     * @param exportType 输出类型
     */
    public void convertFile(String taskId, String srcUri, String exportType) {
        if (StringUtils.isEmpty(taskId)) {
            taskId = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        }
        System.out.println("--convertFile:taskId:-> " + taskId);
        String headerDate = Common.getGMTDate();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("SrcUri", srcUri);
        param.put("FileName", FileUtil.getFileName(srcUri));
        param.put("ExportType", exportType);
        Integer port = null;
        if (serverProperties.getPort() != 443 && serverProperties.getPort() != 80) {
            port = serverProperties.getPort();
        }
        param.put("CallBack", serverProperties.getDomain() + (port == null ? "" : (":" + port)) + "/v1/3rd/file/convertCallback");//回调地址，文件转换后的通知地址，需保证可访问
        param.put("TaskId", taskId);
        //Content-MD5 表示请求内容数据的MD5值，对消息内容（不包括头部）计算MD5值获得128比特位数字，对该数字进行base64编码而得到，如”eB5eJF1ptWaXm4bijSPyxw==”，也可以为空；
        String contentMd5 = Common.getMD5(param);
        //签名url的参数不带请求参数
        String authorization = SignatureUtil.getAuthorization("POST", convertProperties.getConvert(), contentMd5, headerDate, convertProperties.getAppid(), convertProperties.getAppsecret()); //签名

        //header参数
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, Common.CONTENTTYPE);
        headers.put(HttpHeaders.DATE, headerDate);
        headers.put(HttpHeaders.CONTENT_MD5, contentMd5);//文档上是 "Content-Md5"
        headers.put(HttpHeaders.AUTHORIZATION, authorization);

        // 请求
        String result = HttpUtil.post(convertProperties.getConvert(), headers, JSON.toJSONString(param));
        if (!StringUtils.isEmpty(result)) {
            JSONObject dataJson = JSON.parseObject(result);
            String code = dataJson.get("Code").toString();
            if (code.equals("OK")) {
                //成功，做其它业务处理
            } else {
                String errorMsg = "文件格式转换失败";
                if (dataJson.get("Message") != null) {
                    String message = dataJson.get("Message").toString();
                    errorMsg = errorMsg + message;
                }
                //失败
            }
        }
    }

    /**
     * 文件格式转换回调
     */
    public void convertCallBack(HttpServletRequest request) {
        try {
            BufferedReader buf = request.getReader();
            String str;
            StringBuilder data = new StringBuilder();
            while ((str = buf.readLine()) != null) {
                data.append(str);
            }
            logger.info("文件转换callBask取得data={}", data);
            if (data.length() > 0) {
                JSONObject dataJson = JSON.parseObject(data.toString());
                if (dataJson.get("Code") != null) {
                    String code = (String) dataJson.get("Code");
                    String taskId = (String) dataJson.get("TaskId");
                    String url = getConvertQueryRes(taskId);
                    if (!StringUtils.isEmpty(url) && code.equalsIgnoreCase(HttpStatus.OK.getReasonPhrase())) {
                        //
                        System.out.println(url);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 文件转换查询
     *
     * @param taskId 任务id，由convertFil接口生成
     */
    public String getConvertQueryRes(String taskId) {
        String headerDate = Common.getGMTDate();
        String downLoadUrl = "";
        try {
            //请求参数
            String contentMd5 = Common.getMD5(null); //请求内容数据的MD5值，用null作入参
            String url = convertProperties.getQuery() + "?TaskId=" + taskId + "&AppId=" + convertProperties.getAppid();
            String authorization = SignatureUtil.getAuthorization("GET", url, contentMd5, headerDate, convertProperties.getAppid(), convertProperties.getAppsecret()); //签名

            //header参数
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, Common.CONTENTTYPE);
            headers.put(HttpHeaders.DATE, headerDate);
            headers.put(HttpHeaders.CONTENT_MD5, contentMd5);//文档上是 "Content-Md5"
            headers.put(HttpHeaders.AUTHORIZATION, authorization);

            //开始调用
            String result = HttpUtil.get(url, headers);
            if (!StringUtils.isEmpty(result)) {
                JSONObject dataJson = JSON.parseObject(result);
                String code = dataJson.get("Code").toString();
                if (code.equals("OK")) {
                    if (dataJson.get("Urls") != null) { //实际上返回这个参数
                        downLoadUrl = (dataJson.get("Urls")).toString();
                        // 源["xxx"]转换
                        JSONArray jsonArray = JSONArray.parseArray(downLoadUrl);
                        downLoadUrl = jsonArray.get(0).toString();
                    } else if (dataJson.get("Url") != null) {//文档是返回这个参数
                        downLoadUrl = dataJson.get("Url").toString();
                    }
                    //成功
                } else {
                    String errorMsg = "文件格式转换失败";
                    if (dataJson.get("Message") != null) {
                        String message = dataJson.get("Message").toString();
                        errorMsg = errorMsg + message;
                    }
                    //失败
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("recordWPSConvertResult处理出错，错误={}", e.getMessage(), e);
        }
        return downLoadUrl;
    }

}
