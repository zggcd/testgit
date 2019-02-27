package com.hanweb.jmp.cms.controller.matters.doc;

import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.hanweb.common.BaseInfo;
import com.hanweb.common.util.JsonUtil;
import com.hanweb.common.util.NumberUtil;
import com.hanweb.common.util.SpringUtil;
import com.hanweb.common.util.StringUtil;
import com.hanweb.common.util.mvc.ControllerUtil;
import com.hanweb.common.util.mvc.JsonResult;
import com.hanweb.common.util.mvc.ResultState;
import com.hanweb.common.util.mvc.Script;
import com.hanweb.complat.constant.Settings;
import com.hanweb.complat.entity.TempFile;
import com.hanweb.complat.exception.OperationException;
import com.hanweb.complat.listener.UserSessionInfo;
import com.hanweb.complat.service.TempFileService;

import com.hanweb.jmp.constant.Configs;
import com.hanweb.jmp.cms.entity.matters.doc.Doc;
import com.hanweb.jmp.cms.entity.matters.doc.DocCol;
import com.hanweb.jmp.cms.service.matters.doc.DocService;
import com.hanweb.jmp.cms.service.matters.doc.DocColService;
import com.hanweb.support.controller.CurrentUser;

@Controller 
@RequestMapping("manager/matter/doc")
public class OprDocController {

	/**
	 * docService
	 */
	@Autowired
	private DocService docService;
	
	/**
	 * DocColService
	 */
	@Autowired
	private DocColService docColService;
	
	/**
	 * tempFileService
	 */
	@Autowired
	private TempFileService tempFileService;
	
	/**
	 * 新增页面 
	 * @param request   request
	 * @return   ModelAndView
	 */
	@RequestMapping(value = "add_show")
	public ModelAndView showAdd(HttpServletRequest request, Integer classId) {
		ModelAndView modelAndView = new ModelAndView("jmp/cms/matters/doc/doc_add");
		Doc doc = new Doc();
		Configs configs = new Configs();
		String doctype = configs.getDocFileType();
		Integer docsize = configs.getDocFileSize();
		doc.setClassId(NumberUtil.getInt(classId));
		modelAndView.addObject("url", "add_submit.do");
		modelAndView.addObject("doctype", doctype);
		modelAndView.addObject("docsize", docsize);
		modelAndView.addObject("classId", classId);
		modelAndView.addObject("uploadUrl", BaseInfo.getContextPath() + "/manager/matter/doc/multifileupload.do");
		modelAndView.addObject("doc", doc);
		return modelAndView;
	}
	
	/**
	 * 文件上传页面
	 * @return
	 */
	@RequestMapping("multifileupload")
	public ModelAndView add() {
		ModelAndView modelAndView = new ModelAndView("jmp/cms/matters/doc/upload");
		modelAndView.addObject("uploadUrl", BaseInfo.getContextPath() + "/manager/matter/doc/fileupload.do");
		return modelAndView;
	}
	
	/**
	 * 文件上传提交
	 * @param file
	 * @return
	 * @throws Exception
	 */
	@ResponseBody
	@RequestMapping("fileupload")
	public String upload(MultipartFile file) throws Exception {
		String result = "{}";
		if (file == null || file.isEmpty()) {
			return result;
		}
		Settings settings = Settings.getSettings();
		String fileName = file.getOriginalFilename();
		String newName = StringUtil.getUUIDString();
		String fileType = null;
		long fileSize = file.getSize();
		if (fileName.contains(".")) {
			int index = fileName.lastIndexOf(".") + 1;
			fileType = fileName.substring(index);
			newName = newName + "." + fileType.toLowerCase();
		}
		boolean isSuccess = ControllerUtil.writeMultipartFileToFile(new File(settings.getFileTmp()+ newName), file);
		if (isSuccess) {
			TempFile tempFile = new TempFile();
			tempFile.setTmpPath(settings.getFileTmp());
			tempFile.setOldName(fileName);
			tempFile.setNewName(newName);
			tempFile.setUploadDate(new Date());
			if (!StringUtil.isEmpty(fileType)) {
				tempFile.setFileType(fileType.toUpperCase());
			}
			CurrentUser currentUser = UserSessionInfo.getCurrentUser();
			if (currentUser != null) {
				tempFile.setLoginName(currentUser.getLoginName());
			}
			tempFile.setFileSize(fileSize);
			String uuid = tempFileService.add(tempFile);
			result = "{\"uuid\" : \"" + uuid + "\",\"newName\":\"" + newName + "\"}";
		} else {
			result = "{}";
		}
		return result;
	}
	
	/**
	 * 新增提交
	 * @param video
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(value = "add_submit")
	@ResponseBody
	public String saveAdd(String filesJson,String fileinfo,DocFormBean doc, int classId) {
		String message = "";
		boolean isSuccess = false;
		CurrentUser currentUser = UserSessionInfo.getCurrentUser();
		doc.setSiteId(currentUser.getSiteId());
		doc.setIsremove(0);
		DocCol docCol = docColService.findByIid(classId);
		if(docCol!=null){
			doc.setPname(docCol.getName());
		}
		if (!StringUtil.equals("{}", filesJson) && StringUtil.isNotEmpty(filesJson)) {
			Map<String, Map<String, String>> idMap = JsonUtil.StringToObject(filesJson, Map.class);
			Set<String> idSet = idMap.keySet();
			String fileName = "";
			for (String uuid : idSet) {
				File file = tempFileService.findFileByUuid(uuid);
				if (!file.exists()) {
					message += SpringUtil.getMessage("import.nofile") + "<br/>";
				} else {
					fileName = StringUtil.getString(idMap.get(uuid));
					// 文件类型
					int nPot = fileName.lastIndexOf(".");
					String fileType = fileName.substring(nPot + 1, fileName.length()).toLowerCase();
					// 得到系统时间
					java.util.Date current = new java.util.Date();
					java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyMMddHHmmssSSS");
					Random rd = new Random();
					@SuppressWarnings("unused")
					String strNewName = formatter.format(current) + (9999 - rd.nextInt(8999)) + "." + fileType;
					doc.setName(fileName);
				}
				try {
					isSuccess = docService.add(doc, file);
				} catch (OperationException e) {
					message = e.getMessage();
				}
			}
		}
		Script script = Script.getInstanceWithJsLib();
		if (isSuccess) {
			script.addScript("parent.refreshParentWindow();parent.closeDialog();");
			script.addScript("parent.getDialog().dialog('options').callback();");
			script.addScript("parent.closeDialog();");
		} else {
			script.addAlert("新增失败！" + message); 
		}  
		return script.getScript(); 
	}
	
	/**
	 * 修改页面
	 * @param request   request
	 * @param iid       iid
	 * @return   ModelAndView
	 */
	@RequestMapping(value = "modify_show")
	public ModelAndView showModify(HttpServletRequest request, Integer iid) {
		ModelAndView modelAndView = new ModelAndView("jmp/cms/matters/doc/doc_opr");
		Doc doc = docService.findByIid(iid);
		modelAndView.addObject("url", "modify_submit.do");
		modelAndView.addObject("doc", doc);
		return modelAndView;
	}
	
	/**
	 * 修改数据库操作 
	 * @param picture
	 * @return   JsonResult
	 */
	@RequestMapping(value = "modify_submit")
	@ResponseBody
	public String submitModify(DocFormBean doc) {
		String message = "";
		boolean isSuccess = false;
		try {
			isSuccess = docService.modify(doc);
		} catch (OperationException e) {
			message = e.getMessage();
		}
		Script script = Script.getInstanceWithJsLib();
		if (isSuccess) {
			script.addScript("parent.refreshParentWindow();parent.closeDialog();");  
		} else {
			script.addAlert("修改失败！" + message); 
		}
		return script.getScript();
	}
	
	/**
	 * 删除页
	 * @param ids ids
	 * @param idlength idlength
	 * @return    设定参数 .
	 */
	@RequestMapping("remove_show")
	public ModelAndView showRemove(String ids, Integer idlength){ 
		ModelAndView modelAndView = new ModelAndView("jmp/cms/matters/doc/removedoc_opr"); 
		modelAndView.addObject("url", "remove.do");
		modelAndView.addObject("idlength", idlength); 
		modelAndView.addObject("ids", ids);  
		return modelAndView;
	}
	
	/**
	 * 删除 
	 * @param ids  ids
	 * @return JsonResult
	 */
	@RequestMapping(value = "remove")
	@ResponseBody
	public JsonResult remove(DocFormBean doc,String ids,Integer siteId,Integer showList) {
		boolean isSuccess = false;
		JsonResult jsonResult = JsonResult.getInstance();
		CurrentUser currentUser = UserSessionInfo.getCurrentUser();
		doc.setSiteId(currentUser.getSiteId());
		siteId = doc.getSiteId();
		try {
			if(NumberUtil.getInt(showList)==1){
				isSuccess = docService.modifyIsRemove(1,ids, siteId);
			}else{
				isSuccess = docService.removeByIds(ids,siteId);
				}
			if (isSuccess) {
				jsonResult.set(ResultState.REMOVE_SUCCESS);
				jsonResult.addParam("remove", ids);
			} else {
				jsonResult.set(ResultState.REMOVE_FAIL);
			}
		} catch (OperationException e) {
			jsonResult.setMessage(e.getMessage());
		}
		return jsonResult;
	}
	
}