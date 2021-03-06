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
     * ???????????????URL
     *
     * @param fileUrl    ??????url
     * @param checkToken ????????????token
     */
    public Token getViewUrl(String fileUrl, boolean checkToken) {
        Token t = new Token();

        String fileType = FileUtil.getFileTypeByPath(fileUrl);
        // fileId??????uuid?????????????????????????????????????????????
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
     * ???????????????URL
     *
     * @param fileId     ??????id
     * @param userId     ??????id
     * @param checkToken ????????????token
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
     * ???????????????URL
     *
     * @param filePath ????????????
     * @param userId   ??????id
     * @param type     ????????????????????????
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
     * ?????????????????????
     *
     * @param filePath ????????????
     */
    private Map<String, Object> getWebFileInfo(String filePath) {
        logger.info("_w_filepath:{}", filePath);

        // ????????????user??????
        UserDTO wpsUser = new UserDTO(
                "-1", "???", "read", "https://zmfiletest.oss-cn-hangzhou.aliyuncs.com/user0.png"
        );

        int fileSize = FileUtil.getFileSize(filePath);

        // ????????????
        FileDTO file = new FileDTO(
                Context.getFileId(), FileUtil.getFileName(filePath),
                1, fileSize, "-1", new Date().getTime(), filePath,
                // ???????????????????????????????????????
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
     * ?????????????????????
     *
     * @param userId ??????id
     */
    private Map<String, Object> getDbFileInfo(String userId) {
        String fileId = Context.getFileId();

        // ??????????????????
        FileEntity fileEntity = this.findOne(fileId);

        // ??????????????????????????????read
        String permission;

        // ??????????????????
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

        // ????????????
        WatermarkEntity watermarkEntity = watermarkService.getRepository().findFirstByFileId(fileId);
        WatermarkBO watermark = new WatermarkBO();
        if (watermarkEntity != null) {
            BeanUtils.copyProperties(watermarkEntity, watermark);
        }

        //??????user
        UserEntity wpsUser = userService.findOne(userId);
        UserDTO user = new UserDTO();
        if (wpsUser != null) {
            BeanUtils.copyProperties(wpsUser, user);
            user.setPermission(permission);
        }

        // ??????fileInfo
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
     * ???????????????
     *
     * @param fileName ?????????
     * @param userId   ??????id
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
     * ????????????
     *
     * @param file   ??????
     * @param userId ??????id
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
        // ????????????
        FileEntity f = new FileEntity(fileName, 1, fileSize, userId, userId, dataTime, dataTime, fileUrl);
        this.save(f);

        // ????????????
        userAclService.saveUserFileAcl(userId, f.getId());

        // ????????????
        watermarkService.saveWatermark(f.getId());

        // ????????????
        Map<String, Object> map = new HashMap<>();
        map.put("redirect_url", this.getViewUrl(f.getId(), userId, false).getWpsUrl());
        map.put("user_id", userId);
        return map;
    }

    /**
     * ????????????????????????
     */
    public Map<String, Object> fileHistory(FileReqDTO req) {
        List<FileHisDTO> result = new ArrayList<>(1);
        if (req.getId() != null) {
            // ??????????????????????????????????????????
            List<FileVersionEntity> versionList =
                    fileVersionService.getRepository().findByFileIdOrderByVersionDesc(req.getId());
            if (versionList != null && versionList.size() > 0) {
                Set<String> userIdSet = new HashSet<>();
                for (FileVersionEntity fileVersion : versionList) {
                    userIdSet.add(fileVersion.getModifier());
                    userIdSet.add(fileVersion.getCreator());
                }
                List<String> userIdList = new ArrayList<>(userIdSet);
                // ????????????????????????
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
     * ????????????
     *
     * @param mFile  ??????
     * @param userId ??????id
     */
    public Map<String, Object> fileSave(MultipartFile mFile, String userId) {
        Date date = new Date();
        // ??????

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

        // ??????????????????
        file.setVersion(file.getVersion() + 1);
        file.setDownload_url(resFileDTO.getFileUrl());
        file.setModifier(userId);
        file.setModify_time(date.getTime());
        file.setSize(size);
        this.update(file);

        // ??????????????????
        FileVersionEntity fileVersion = new FileVersionEntity();
        BeanUtils.copyProperties(file, fileVersion);
        fileVersion.setFileId(fileId);
        fileVersion.setVersion(file.getVersion() - 1);
        fileVersion.setDownload_url(oldFileUrl);
        fileVersion.setSize(size);
        fileVersionService.save(fileVersion);

        // ????????????????????????
        BeanUtils.copyProperties(file, fileInfo);

        Map<String, Object> map = new HashMap<>();
        map.put("file", fileInfo);
        return map;
    }

    /**
     * ??????????????????
     *
     * @param version ?????????
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
     * ??????????????????
     */
    public List<FileListDTO> getFileList() {
        return this.getRepository().findAllFile();
    }

    /**
     * ??????????????????--??????
     */
    public Page<FileListDTO> getFileListByPage(com.web.wps.base.Page page) {
        PageRequest pages = new PageRequest(page.getPage() - 1, page.getSize());
        return this.getRepository().getAllFileByPage(pages);
    }

    /**
     * ????????????
     *
     * @param id ??????id
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
     * ????????????
     *
     * @param file ??????
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
        // ??????????????????????????????????????????
        Date date = new Date();
        long dataTime = date.getTime();
        // ????????????
        FileEntity f = new FileEntity(resFileDTO.getFileName(), 1, ((int) resFileDTO.getFileSize()),
                uploadUserId, uploadUserId, dataTime, dataTime, resFileDTO.getFileUrl());
        this.save(f);

        // ????????????
        userAclService.saveUserFileAcl(uploadUserId, f.getId());

        // ????????????
        watermarkService.saveWatermark(f.getId());
    }

    /**
     * ??????????????????
     */
    public String createTemplateFile(String template) {
        boolean typeTrue = FileUtil.checkCode(template);
        if (typeTrue) {
            return wpsUtil.getTemplateWpsUrl(template, "3");
        }
        return "";
    }

    /**
     * ???????????????	???????????????
     * word			pdf???png
     * excel		pdf???png
     * ppt			pdf
     * pdf			word???ppt???excel
     *
     * @param srcUri     ??????url
     * @param exportType ????????????
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
        param.put("CallBack", serverProperties.getDomain() + (port == null ? "" : (":" + port)) + "/v1/3rd/file/convertCallback");//??????????????????????????????????????????????????????????????????
        param.put("TaskId", taskId);
        //Content-MD5 ???????????????????????????MD5????????????????????????????????????????????????MD5?????????128????????????????????????????????????base64????????????????????????eB5eJF1ptWaXm4bijSPyxw==????????????????????????
        String contentMd5 = Common.getMD5(param);
        //??????url???????????????????????????
        String authorization = SignatureUtil.getAuthorization("POST", convertProperties.getConvert(), contentMd5, headerDate, convertProperties.getAppid(), convertProperties.getAppsecret()); //??????

        //header??????
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, Common.CONTENTTYPE);
        headers.put(HttpHeaders.DATE, headerDate);
        headers.put(HttpHeaders.CONTENT_MD5, contentMd5);//???????????? "Content-Md5"
        headers.put(HttpHeaders.AUTHORIZATION, authorization);

        // ??????
        String result = HttpUtil.post(convertProperties.getConvert(), headers, JSON.toJSONString(param));
        if (!StringUtils.isEmpty(result)) {
            JSONObject dataJson = JSON.parseObject(result);
            String code = dataJson.get("Code").toString();
            if (code.equals("OK")) {
                //??????????????????????????????
            } else {
                String errorMsg = "????????????????????????";
                if (dataJson.get("Message") != null) {
                    String message = dataJson.get("Message").toString();
                    errorMsg = errorMsg + message;
                }
                //??????
            }
        }
    }

    /**
     * ????????????????????????
     */
    public void convertCallBack(HttpServletRequest request) {
        try {
            BufferedReader buf = request.getReader();
            String str;
            StringBuilder data = new StringBuilder();
            while ((str = buf.readLine()) != null) {
                data.append(str);
            }
            logger.info("????????????callBask??????data={}", data);
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
     * ??????????????????
     *
     * @param taskId ??????id??????convertFil????????????
     */
    public String getConvertQueryRes(String taskId) {
        String headerDate = Common.getGMTDate();
        String downLoadUrl = "";
        try {
            //????????????
            String contentMd5 = Common.getMD5(null); //?????????????????????MD5?????????null?????????
            String url = convertProperties.getQuery() + "?TaskId=" + taskId + "&AppId=" + convertProperties.getAppid();
            String authorization = SignatureUtil.getAuthorization("GET", url, contentMd5, headerDate, convertProperties.getAppid(), convertProperties.getAppsecret()); //??????

            //header??????
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, Common.CONTENTTYPE);
            headers.put(HttpHeaders.DATE, headerDate);
            headers.put(HttpHeaders.CONTENT_MD5, contentMd5);//???????????? "Content-Md5"
            headers.put(HttpHeaders.AUTHORIZATION, authorization);

            //????????????
            String result = HttpUtil.get(url, headers);
            if (!StringUtils.isEmpty(result)) {
                JSONObject dataJson = JSON.parseObject(result);
                String code = dataJson.get("Code").toString();
                if (code.equals("OK")) {
                    if (dataJson.get("Urls") != null) { //???????????????????????????
                        downLoadUrl = (dataJson.get("Urls")).toString();
                        // ???["xxx"]??????
                        JSONArray jsonArray = JSONArray.parseArray(downLoadUrl);
                        downLoadUrl = jsonArray.get(0).toString();
                    } else if (dataJson.get("Url") != null) {//???????????????????????????
                        downLoadUrl = dataJson.get("Url").toString();
                    }
                    //??????
                } else {
                    String errorMsg = "????????????????????????";
                    if (dataJson.get("Message") != null) {
                        String message = dataJson.get("Message").toString();
                        errorMsg = errorMsg + message;
                    }
                    //??????
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("recordWPSConvertResult?????????????????????={}", e.getMessage(), e);
        }
        return downLoadUrl;
    }

}
