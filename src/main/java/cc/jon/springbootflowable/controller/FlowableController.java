package cc.jon.springbootflowable.controller;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * @description: // TODO
 * @author: Johnny Jiang
 * @date: 2018/10/24 17:13
 */

@Controller
@RequestMapping("/flowable")
public class FlowableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowableController.class);

    @Autowired
    ManagementService managementService;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    HistoryService historyService;

    @Autowired
    FormService formService;

    @Autowired
    IdentityService identityService;

    @RequestMapping("")
    public String index() {
        return "templates/layout";
    }

    @RequestMapping("/testJsp")
    public String testJsp() {
        return "index";
    }

    @ResponseBody
    @RequestMapping("/engine/info")
    public Map<String, String> engineProperties() {
        return managementService.getProperties();
    }

    /**
     * 部署流程资源
     */
    @RequestMapping(value = "/deploy")
    public String deploy(@RequestParam(value = "file", required = true) MultipartFile file) {

        // 获取上传的文件名
        String fileName = file.getOriginalFilename();
        DeploymentBuilder deployment = null;
        try {
            // 得到输入流（字节流）对象
            try (InputStream fileInputStream = file.getInputStream()) {
                // 文件的扩展名
                String extension = FilenameUtils.getExtension(fileName);

                // zip或者bar类型的文件用ZipInputStream方式部署
                deployment = repositoryService.createDeployment();
                if ("zip".equals(extension) || "bar".equals(extension)) {
                    ZipInputStream zip = new ZipInputStream(fileInputStream);
                    deployment.addZipInputStream(zip);
                } else {
                    // 其他类型的文件直接部署
                    deployment.addInputStream(fileName, fileInputStream);
                }
            }
            deployment.deploy();

        } catch (Exception e) {
            LOGGER.error("error on deploy process, because of file input stream");
        }
        return "redirect:/flowable/process-list";
    }

    /**
     * 启动流程
     */
    @RequestMapping("/process/start/{processDefinitionId}")
    public String startProcess(@PathVariable("processDefinitionId") String processDefinitionId) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceById(processDefinitionId);
        LOGGER.info("成功启动了流程：" + processInstance.getId());
        return "redirect:/flowable/task-list";
    }

    /**
     * 流程定义列表
     */
    @RequestMapping("/process-list")
    public ModelAndView processes() {
        ModelAndView mav = new ModelAndView("templates/processes");
        List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery().list();

        mav.addObject("processes", list);
        return mav;
    }

    /**
     * 任务列表
     */
    @RequestMapping("/task-list")
    public ModelAndView tasks() {
        ModelAndView mav = new ModelAndView("templates/tasks");
        List<Task> list = taskService.createTaskQuery().list();
        mav.addObject("tasks", list);
        return mav;
    }

    /**
     * 完成任务
     */
    @RequestMapping("/task/complete/{taskId}")
    public String completeTask(@PathVariable("taskId") String taskId) {
//        out:println "applyUser:" + applyUser + " ,days:" + days + ", approval:" + approved;
        Map<String, Object> variables = new HashMap<>();
        variables.put("applyUser", "jiang");
        variables.put("days", 3);
        variables.put("approved", true);

        taskService.complete(taskId, variables);
        return "redirect:/flowable/task-list";
    }

    /**
     * 删除部署的流程，级联删除流程实例
     *
     * @param deploymentId 流程部署ID
     */
//    @RequestMapping(value = "/delete-deployment")
//    public String deleteProcessDefinition(@RequestParam("deploymentId") String deploymentId) {@RequestMapping(value = "/delete-deployment")
    @RequestMapping("/process/delete-deployment/{deploymentId}")
    public String deleteProcessDefinition(@PathVariable("deploymentId") String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
        return "redirect:/flowable/process-list";
    }

    /**
     * 读取流程资源
     *
     * @param processDefinitionId 流程定义ID
     * @param resourceName        资源名称
     */
    @RequestMapping(value = "/read-resource")
    public void readResource(@RequestParam("pdid") String processDefinitionId, @RequestParam("resourceName") String resourceName, HttpServletResponse response)
            throws Exception {
        ProcessDefinitionQuery pdq = repositoryService.createProcessDefinitionQuery();
        ProcessDefinition pd = pdq.processDefinitionId(processDefinitionId).singleResult();

        // 通过接口读取
        InputStream resourceAsStream = repositoryService.getResourceAsStream(pd.getDeploymentId(), resourceName);

        // 输出资源内容到相应对象
        byte[] b = new byte[1024];
        int len = -1;
        while ((len = resourceAsStream.read(b, 0, 1024)) != -1) {
            response.getOutputStream().write(b, 0, len);
        }
    }

    /**
     * 已完成流程列表
     */
    @RequestMapping("/history-list")
    public ModelAndView history() {
        ModelAndView mav = new ModelAndView("templates/history");
        List<HistoricProcessInstance> list = historyService.createHistoricProcessInstanceQuery().finished().list();
        mav.addObject("histories", list);
        return mav;
    }

    /**
     * 所有历史任务活动列表
     */
    @RequestMapping("/history-activities")
    public ModelAndView historyAct() {
        ModelAndView mav = new ModelAndView("templates/activities");
        List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
                .orderByProcessInstanceId().asc()
                .orderByTaskCreateTime().asc()
                .list();
        mav.addObject("activities", list);
        return mav;
    }

    /**
     * 历史任务活动列表
     */
    @RequestMapping("/history-activity")
    public ModelAndView historyActByProcessInsId() {
        ModelAndView mav = new ModelAndView("templates/activities");
        List<HistoricActivityInstance> list = historyService.createHistoricActivityInstanceQuery().list();
        mav.addObject("activities", list);
        return mav;
    }

    /**
     * 生成流程图
     *
     * @param deploymentId 任务ID
     */
    @RequestMapping(value = "/process/processDiagram/{deploymentId}")
    public void genProcessDiagram(HttpServletResponse httpServletResponse, @PathVariable("deploymentId") String deploymentId) throws Exception {
        List<String> names = repositoryService.getDeploymentResourceNames(deploymentId);
        String imageName = "";
        for (String name : names) {
            if (name.indexOf(".png") >= 0) {
                imageName = name;
            }
        }

        if (!StringUtils.isEmpty(imageName)) {
            byte[] buf = new byte[1024];
            try (InputStream in = repositoryService.getResourceAsStream(deploymentId, imageName)) {
                try (OutputStream out = httpServletResponse.getOutputStream()) {
                    int length = 0;
                    while ((length = in.read(buf)) != -1) {
                        out.write(buf, 0, length);
                    }
                }
            }
        }
    }

    /**
     * reassign
     */
    @RequestMapping("/reassign")
    public ModelAndView reassignTask() {
        ModelAndView mav = new ModelAndView("templates/activities");
//        List<HistoricActivityInstance> list = historyService.createHistoricActivityInstanceQuery().list();
//        mav.addObject("activities", list);
        return mav;
    }

}
