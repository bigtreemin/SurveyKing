package cn.surveyking.server.impl;

import cn.surveyking.server.core.common.Tuple2;
import cn.surveyking.server.core.constant.AppConsts;
import cn.surveyking.server.core.constant.ErrorCode;
import cn.surveyking.server.core.constant.FieldPermissionType;
import cn.surveyking.server.core.constant.ProjectModeEnum;
import cn.surveyking.server.core.exception.ErrorCodeException;
import cn.surveyking.server.core.uitls.*;
import cn.surveyking.server.domain.dto.*;
import cn.surveyking.server.domain.mapper.ProjectViewMapper;
import cn.surveyking.server.domain.model.Answer;
import cn.surveyking.server.service.AnswerService;
import cn.surveyking.server.service.ProjectService;
import cn.surveyking.server.service.SurveyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author javahuang
 * @date 2021/8/22
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SurveyServiceImpl implements SurveyService {

	private final ProjectService projectService;

	private final ProjectViewMapper projectViewMapper;

	private final AnswerService answerService;

	/**
	 * answerService 如果需要验证密码，则只有密码输入正确之后才开始加载 schema
	 * @param projectId
	 * @return
	 */
	@Override
	public PublicProjectView loadProject(String projectId) {
		ProjectView project = projectService.getProject(projectId);
		if (project == null) {
			throw new ErrorCodeException(ErrorCode.ProjectNotFound);
		}
		PublicProjectView projectView = projectViewMapper.toPublicProjectView(project);

		// 需要密码答卷
		if (project != null && project.getSetting() != null && project.getSetting().getAnswerSetting() != null
				&& project.getSetting().getAnswerSetting().getPassword() != null) {
			projectView.setSurvey(null);
			projectView.setPasswordRequired(true);
		}

		// 需要登录答卷
		if (Boolean.TRUE.equals(projectView.getSetting().getAnswerSetting().getLoginRequired())
				&& !SecurityContextUtils.isAuthenticated()) {
			projectView.setLoginRequired(true);
			projectView.setSurvey(null);
		}

		// 允许修改答案
		if (Boolean.TRUE.equals(projectView.getSetting().getSubmittedSetting().getEnableUpdate())
				&& SecurityContextUtils.isAuthenticated()) {
			AnswerQuery answerQuery = new AnswerQuery();
			answerQuery.setProjectId(projectId);
			answerQuery.setLatest(true);
			AnswerView latestAnswer = answerService.getAnswer(answerQuery);
			if (latestAnswer != null) {
				projectView.setAnswerId(latestAnswer.getId());
			}
		}
		// 校验问卷
		validateProject(project);
		return projectView;
	}

	@Override
	public PublicProjectView verifyPassword(ProjectQuery query) {
		ProjectView project = projectService.getProject(query.getId());
		if (project.getSetting().getAnswerSetting().getPassword().equals(query.getPassword())) {
			return projectViewMapper.toPublicProjectView(project);
		}
		throw new ErrorCodeException(ErrorCode.ValidationError);
	}

	/**
	 * 根据问卷设置校验问卷
	 * @param project
	 * @return
	 */
	private ProjectSetting validateProject(ProjectView project) {
		ProjectSetting setting = project.getSetting();
		String projectId = project.getId();
		if (setting.getStatus() == 0) {
			throw new ErrorCodeException(ErrorCode.SurveySuspend);
		}

		Long maxAnswers = setting.getAnswerSetting().getMaxAnswers();
		// 校验最大答案条数限制
		if (maxAnswers != null) {
			AnswerQuery answerQuery = new AnswerQuery();
			answerQuery.setProjectId(project.getId());
			long totalAnswers = answerService.count(answerQuery);
			if (totalAnswers >= maxAnswers) {
				throw new ErrorCodeException(ErrorCode.ExceededMaxAnswers);
			}
			// 不将设置暴露给前端接口，不使用 @JsonProperty，否则设置页面回获取不到该值
			setting.getAnswerSetting().setMaxAnswers(null);
		}
		// 校验问卷是否已到结束时间
		Long endTime = setting.getAnswerSetting().getEndTime();
		if (endTime != null) {
			if (new Date().getTime() > endTime) {
				throw new ErrorCodeException(ErrorCode.ExceededEndTime);
			}
		}
		// 如果需要登录，则使用账号进行限制
		if (setting.getAnswerSetting().getLoginLimit() != null
				&& Boolean.TRUE.equals(setting.getAnswerSetting().getLoginRequired())) {
			validateLoginLimit(projectId, setting);
		}
		// cookie 限制
		if (setting.getAnswerSetting().getCookieLimit() != null) {
			validateCookieLimit(projectId, setting);
		}
		// ip 限制
		if (setting.getAnswerSetting().getIpLimit() != null) {
			validateIpLimit(projectId, setting);
		}
		validateExamSetting(project);
		return setting;
	}

	private void validateExamSetting(ProjectView project) {
		ProjectSetting.ExamSetting examSetting = project.getSetting().getExamSetting();
		if (examSetting == null || !ProjectModeEnum.exam.equals(project.getMode())) {
			return;
		}
		// 校验考试开始时间
		if (examSetting.getStartTime() != null && new Date(examSetting.getStartTime()).compareTo(new Date()) > 0) {
			throw new ErrorCodeException(ErrorCode.ExamUnStarted);
		}
		// 校验考试结束时间
		if (examSetting.getEndTime() != null && new Date(examSetting.getEndTime()).compareTo(new Date()) < 0) {
			throw new ErrorCodeException(ErrorCode.ExamFinished);
		}

	}

	@Override
	public PublicStatisticsView statProject(ProjectQuery query) {
		AnswerQuery answerQuery = new AnswerQuery();
		answerQuery.setProjectId(query.getId());
		answerQuery.setPageSize(-1);
		List<AnswerView> answers = answerService.listAnswer(answerQuery).getList();
		ProjectView project = projectService.getProject(query.getId());
		return new ProjectStatHelper(project.getSurvey(), answers).stat();
	}

	@Override
	public PublicAnswerView saveAnswer(AnswerRequest answer, HttpServletRequest request) {
		String projectId = answer.getProjectId();

		PublicAnswerView result = new PublicAnswerView();
		ProjectView project = projectService.getProject(projectId);
		ProjectSetting setting = project.getSetting();

		String answerId;
		if (isBlank(answer.getId())) {
			// 问卷允许修改答案开关修改答案
			answerId = validateAndGetLatestAnswer(project);
		}
		else {
			// 公开查询修改答案
			answerId = answer.getId();
			validateAndMergeAnswer(project, answer);
		}
		answer.setId(answerId);
		AnswerView answerView = answerService.saveAnswer(answer, request);

		// 登录用户无需显示修改答案的二维码
		if (Boolean.TRUE.equals(setting.getSubmittedSetting().getEnableUpdate())
				&& !SecurityContextUtils.isAuthenticated()) {
			result.setAnswerId(answerView.getId());
		}
		if (ProjectModeEnum.exam.equals(project.getMode())) {
			result.setExamScore(answerView.getExamScore());
			result.setQuestionScore(answerView.getExamInfo().getQuestionScore());
		}

		return result;
	}

	@Override
	public PublicAnswerView loadAnswer(AnswerQuery query) {
		ProjectSetting setting = null;
		try {
			ProjectView project = projectService.getProject(query.getProjectId());
			setting = validateProject(project);
		}
		catch (ErrorCodeException e) {
			// 401 开头的是校验问卷限制，修改答案的时候无需校验
			if (!(e.getErrorCode().code + "").startsWith("401")) {
				throw e;
			}
		}
		if (!Boolean.TRUE.equals(setting.getSubmittedSetting().getEnableUpdate())) {
			throw new ErrorCodeException(ErrorCode.AnswerChangeDisabled);
		}
		PublicAnswerView answerView = new PublicAnswerView();
		BeanUtils.copyProperties(answerService.getAnswer(query), answerView);
		return answerView;
	}

	/**
	 * @param request
	 * @return
	 */
	@Override
	@SneakyThrows
	public PublicQueryVerifyView loadQuery(PublicQueryRequest request) {
		Tuple2<ProjectView, ProjectSetting.PublicQuery> projectAndQuery = getProjectAndQueryThenValidate(request);
		SurveySchema schema = buildQueryFormSchema(projectAndQuery.getFirst(), projectAndQuery.getSecond());
		PublicQueryVerifyView view = new PublicQueryVerifyView();
		view.setSchema(schema);
		return view;
	}

	@Override
	public PublicQueryView getQueryResult(PublicQueryRequest request) {
		Tuple2<ProjectView, ProjectSetting.PublicQuery> projectAndQuery = getProjectAndQueryThenValidate(request);
		SurveySchema schema = buildQueryResultSchema(projectAndQuery.getFirst(), projectAndQuery.getSecond());
		PublicQueryView view = new PublicQueryView();
		view.setSchema(schema);
		List<Answer> answers = findAnswerByQuery(request, projectAndQuery);
		answers.forEach(answer -> {
			filterAnswerByFieldPermission(answer.getAnswer(), projectAndQuery.getSecond().getFieldPermission());
		});

		view.setAnswers(answers.stream().map(x -> {
			PublicAnswerView answerView = new PublicAnswerView();
			answerView.setAnswerId(x.getId());
			answerView.setAnswer(x.getAnswer());
			answerView.setCreateAt(x.getCreateAt());
			return answerView;
		}).collect(Collectors.toList()));
		view.setFieldPermission(projectAndQuery.getSecond().getFieldPermission());
		return view;
	}

	/**
	 * 校验问卷并且判断是否要更新最近一次的答案
	 * @param project
	 * @return
	 */
	private String validateAndGetLatestAnswer(ProjectView project) {
		ProjectSetting setting = project.getSetting();
		boolean needGetLatest = false;
		try {
			validateProject(project);
			// 未设时间限制&需要登录&可以修改，永远修改的是同一份
			if (SecurityContextUtils.isAuthenticated() && setting != null
					&& Boolean.TRUE.equals(setting.getSubmittedSetting().getEnableUpdate())) {
				needGetLatest = true;
			}
		}
		catch (ErrorCodeException e) {
			// 如果设置了时间限制，只能修改某个时间区间内的问卷
			// 登录&问卷已提交&允许修改，则可以继续修改
			if (ErrorCode.SurveySubmitted.equals(e.getErrorCode()) && SecurityContextUtils.isAuthenticated()
					&& setting != null && Boolean.TRUE.equals(setting.getSubmittedSetting().getEnableUpdate())) {
				needGetLatest = true;
			}
			else {
				throw e;
			}
		}
		// 获取最近一份的问卷执行答案更新操作
		if (needGetLatest) {
			AnswerQuery answerQuery = new AnswerQuery();
			answerQuery.setProjectId(project.getId());
			answerQuery.setLatest(true);
			AnswerView latestAnswer = answerService.getAnswer(answerQuery);
			if (latestAnswer != null) {
				return answerQuery.getId();
			}
		}
		return null;
	}

	/**
	 * 公开查询修改答案，因为涉及到权限操作，需要将之前的答案和更新的答案做一个 merge 操作
	 * @param project
	 * @param answer
	 */
	private void validateAndMergeAnswer(ProjectView project, AnswerRequest answer) {
		if (isBlank(answer.getQueryId()) || isBlank(answer.getId())) {
			throw new ErrorCodeException(ErrorCode.QueryResultUpdateError);
		}
		try {
			// 公开查询设置必须存在，并且包含可编辑字段
			ProjectSetting.PublicQuery query = project.getSetting().getSubmittedSetting().getPublicQuery().stream()
					.filter(x -> x.getId().equals(answer.getQueryId())).findFirst().get();
			if (!query.getFieldPermission().values().contains(FieldPermissionType.editable)) {
				throw new ErrorCodeException(ErrorCode.QueryResultUpdateError);
			}
			AnswerQuery answerQuery = new AnswerQuery();
			answerQuery.setId(answer.getId());
			AnswerView latestAnswer = answerService.getAnswer(answerQuery);
			LinkedHashMap<String, Object> existAnswer = latestAnswer.getAnswer();
			existAnswer.forEach((key, value) -> {
				if (!answer.getAnswer().containsKey(key)) {
					answer.getAnswer().put(key, value);
				}
			});
		}
		catch (Exception e) {
			throw new ErrorCodeException(ErrorCode.QueryResultUpdateError);
		}
	}

	private void validateLoginLimit(String projectId, ProjectSetting setting) {
		String userId = SecurityContextUtils.getUserId();
		if (userId == null) {
			log.info("user is empty");
			return;
		}
		ProjectSetting.UniqueLimitSetting loginLimitSetting = setting.getAnswerSetting().getLoginLimit();
		AnswerQuery query = new AnswerQuery();
		query.setProjectId(projectId);
		query.setCreateBy(userId);
		doValidate(loginLimitSetting, query);
	}

	private void validateCookieLimit(String projectId, ProjectSetting setting) {
		HttpServletRequest request = ContextHelper.getCurrentHttpRequest();

		Cookie limitCookie = WebUtils.getCookie(request, AppConsts.COOKIE_LIMIT_NAME);
		if (limitCookie == null) {
			// 添加 cookie
			HttpServletResponse response = ContextHelper.getCurrentHttpResponse();

			final Cookie cookie = new Cookie(AppConsts.COOKIE_LIMIT_NAME, UUID.randomUUID().toString());
			cookie.setPath("/");
			cookie.setMaxAge(100 * 360 * 24 * 60 * 60);
			cookie.setHttpOnly(true);
			response.addCookie(cookie);
			response.addCookie(cookie);
			return;
		}
		ProjectSetting.UniqueLimitSetting uniqueLimitSetting = setting.getAnswerSetting().getCookieLimit();
		AnswerQuery query = new AnswerQuery();
		query.setProjectId(projectId);
		query.setCookie(limitCookie.getValue());
		doValidate(uniqueLimitSetting, query);
	}

	private void validateIpLimit(String projectId, ProjectSetting setting) {
		HttpServletRequest request = ContextHelper.getCurrentHttpRequest();
		String ip = IPUtils.getClientIpAddress(request);
		if (ip == null) {
			log.info("ip is empty");
			return;
		}
		ProjectSetting.UniqueLimitSetting ipLimitSetting = setting.getAnswerSetting().getIpLimit();
		AnswerQuery query = new AnswerQuery();
		query.setProjectId(projectId);
		query.setIp(ip);
		doValidate(ipLimitSetting, query);
	}

	private void doValidate(ProjectSetting.UniqueLimitSetting setting, AnswerQuery query) {
		// 通过 cron 计算时间窗
		CronHelper helper = new CronHelper(setting.getLimitFreq().getCron());
		Tuple2<LocalDateTime, LocalDateTime> currentWindow = helper.currentWindow();
		if (currentWindow != null) {
			query.setStartTime(Date.from(currentWindow.getFirst().atZone(ZoneId.systemDefault()).toInstant()));
			query.setEndTime(Date.from(currentWindow.getSecond().atZone(ZoneId.systemDefault()).toInstant()));
		}
		long total = answerService.count(query);
		if (setting.getLimitNum() != null && total >= setting.getLimitNum()) {
			throw new ErrorCodeException(ErrorCode.SurveySubmitted);
		}
	}

	private Tuple2<ProjectView, ProjectSetting.PublicQuery> getProjectAndQueryThenValidate(PublicQueryRequest request) {
		ProjectView project = projectService.getProject(request.getId());
		List<ProjectSetting.PublicQuery> queries = project.getSetting().getSubmittedSetting().getPublicQuery();
		if (queries == null || queries.size() == 0) {
			throw new ErrorCodeException(ErrorCode.QueryNotExist);
		}
		ProjectSetting.PublicQuery query = queries.stream().filter(x -> x.getId().equals(request.getResultId()))
				.findFirst().orElseThrow(() -> new ErrorCodeException(ErrorCode.QueryNotExist));
		validatePublicQuery(query, request.getAnswer());
		return new Tuple2<>(project, query);
	}

	/**
	 * @param query 公开查询配置
	 * @param answer 查询答案
	 */
	@SneakyThrows
	private void validatePublicQuery(ProjectSetting.PublicQuery query, LinkedHashMap answer) {
		if (Boolean.FALSE.equals(query.getEnabled())) {
			throw new ErrorCodeException(ErrorCode.QueryDisabled);
		}
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		List<String> linkValidityPeriod = query.getLinkValidityPeriod();
		if (linkValidityPeriod != null && linkValidityPeriod.size() == 2 && !DateUtils.isBetween(new Date(),
				sdf.parse(linkValidityPeriod.get(0)), sdf.parse(linkValidityPeriod.get(1)))) {
			throw new ErrorCodeException(ErrorCode.QueryDisabled);
		}
		// 校验密码
		if (isNotBlank(query.getPassword()) && answer != null) {
			if (!answer.containsKey(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID)) {
				throw new ErrorCodeException(ErrorCode.QueryPasswordError);
			}
			String password = (String) ((Map) answer.get(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID))
					.get(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID);
			if (isBlank(password) || !query.getPassword().equals(password.trim())) {
				throw new ErrorCodeException(ErrorCode.QueryPasswordError);
			}
			answer.remove(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID);
		}
	}

	/**
	 * 前端支持动态主题(问卷主题/表单主题)切换，动态构建查询表单
	 * @param project
	 * @param query
	 * @return
	 */
	private SurveySchema buildQueryFormSchema(ProjectView project, ProjectSetting.PublicQuery query) {
		SurveySchema schema = SurveySchema.builder().id(query.getId()).title(query.getTitle())
				.description(query.getDescription())
				.children(findMatchChildrenInSchema(query.getConditionQuestion(), project))
				.attribute(SurveySchema.Attribute.builder().submitButton("查询").build()).build();
		// 目前只支持文本题 #{huaw}#{fhpd}
		if (isNotBlank(query.getPassword())) {
			// 添加一个password的schema，用于密码校验
			SurveySchema passwordSchema = SurveySchema.builder().id(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID)
					.title("密码").type(SurveySchema.QuestionType.FillBlank)
					.attribute(SurveySchema.Attribute.builder().required(true).build()).build();
			passwordSchema.setChildren(Collections
					.singletonList(SurveySchema.builder().id(AppConsts.PUBLIC_QUERY_PASSWORD_FIELD_ID).build()));
			schema.getChildren().add(passwordSchema);
		}
		return schema;
	}

	private List<SurveySchema> findMatchChildrenInSchema(String conditionQuestion, ProjectView project) {
		if (isBlank(conditionQuestion)) {
			return new ArrayList<>();
		}
		Pattern condPattern = Pattern.compile("#\\{(.*?)\\}");
		Matcher m = condPattern.matcher(conditionQuestion);
		List<String> conditionIds = new ArrayList<>();
		while (m.find()) {
			String qId = m.group(1);
			conditionIds.add(qId);
		}
		return SchemaParser.flatSurveySchema(project.getSurvey()).stream()
				.filter(qSchema -> conditionIds.contains(qSchema.getId())).collect(Collectors.toList());
	}

	/**
	 * 根据配置的字段权限信息来过滤要查询的字段
	 * @param project
	 * @param query
	 * @return
	 */
	private SurveySchema buildQueryResultSchema(ProjectView project, ProjectSetting.PublicQuery query) {
		SurveySchema schema = project.getSurvey().deepCopy();
		SchemaParser.updateSchemaByPermission(query.getFieldPermission(), schema);
		if (query.getFieldPermission().values().contains(FieldPermissionType.editable)) {
			schema.setAttribute(SurveySchema.Attribute.builder().submitButton("修改").suffix(null).build());
		}
		else {
			schema.setAttribute(null);
		}
		return schema;
	}

	/**
	 * @param request 提交的请求
	 * @param projectAndQuery 项目信息和当前查询信息
	 * @return
	 */
	private List<Answer> findAnswerByQuery(PublicQueryRequest request,
			Tuple2<ProjectView, ProjectSetting.PublicQuery> projectAndQuery) {
		ProjectView projectView = projectAndQuery.getFirst();
		List<SurveySchema> conditionSchemaList = findMatchChildrenInSchema(
				projectAndQuery.getSecond().getConditionQuestion(), projectAndQuery.getFirst());
		if (conditionSchemaList.size() == 0 && request.getQuery().size() == 0) {
			throw new ErrorCodeException(ErrorCode.QueryResultNotExist);
		}
		SchemaParser.TreeNode treeNode = SchemaParser.SurveySchema2TreeNode(projectView.getSurvey());

		// 通过 url 参数构建查询表单
		LinkedHashMap<String, Map> queryFormValues = buildFormValuesFromQueryParrameter(treeNode, request.getQuery());
		// 将查询表单和url参数构建的查询表单合并
		queryFormValues.putAll(request.getAnswer());

		List<Answer> answer = ((AnswerServiceImpl) answerService)
				.list(Wrappers.<Answer>lambdaQuery().eq(Answer::getProjectId, projectView.getId()).and(i -> {
					queryFormValues.forEach((qId, qValueObj) -> {
						i.like(Answer::getAnswer,
								buildLikeQueryConditionOfQuestion(treeNode.getTreeNodeMap().get(qId), qValueObj));
					});
				}));
		if (answer.size() == 0) {
			throw new ErrorCodeException(ErrorCode.QueryResultNotExist);
		}
		// 根据配置过滤结果
		return answer;
	}

	/**
	 * 通过查询参数里面构建 form values
	 * @param query
	 * @return
	 */
	private LinkedHashMap buildFormValuesFromQueryParrameter(SchemaParser.TreeNode surveySchemaTreeNode,
			Map<String, String> query) {
		LinkedHashMap<String, Map> formValues = new LinkedHashMap<>();
		query.forEach((id, value) -> {
			// 默认为选项
			SchemaParser.TreeNode findNode = surveySchemaTreeNode.getTreeNodeMap().get(id);
			String questionId = findNode.getParent().getData().getId();
			Map questionValueMap = formValues.computeIfAbsent(questionId, k -> new HashMap<>());
			questionValueMap.put(id, value);
		});
		return formValues;
	}

	/**
	 * 通过问题答案手动构建like 查询
	 * @param qNode 当前问题的 schema node 节点
	 * @param qValueObj 当前问题的答案
	 * @return
	 */
	private String buildLikeQueryConditionOfQuestion(SchemaParser.TreeNode qNode, Map qValueObj) {
		SurveySchema optionSchema = qNode.getData().getChildren().get(0);
		String optionId = optionSchema.getId();
		Object optionValue = qValueObj.get(optionId);
		String value = optionValue.toString();
		// 选项非数值类型
		if (optionSchema.getAttribute() == null
				|| !SurveySchema.DataType.number.equals(optionSchema.getAttribute().getDataType())) {
			value = "\"" + value + "\"";
		}
		return String.format("{\"%s\":%s}", optionId, value);
	}

	/**
	 * 根据字段权限配置过滤结果集，过滤掉隐藏题的答案
	 * @param answer
	 * @param fieldPermission
	 */
	private void filterAnswerByFieldPermission(LinkedHashMap answer, LinkedHashMap<String, Integer> fieldPermission) {
		fieldPermission.entrySet().forEach(entry -> {
			String qId = entry.getKey();
			Integer permission = entry.getValue();
			if (FieldPermissionType.hidden == permission) {
				answer.remove(qId);
			}
		});
	}

}
