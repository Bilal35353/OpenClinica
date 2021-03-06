/*
 * OpenClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: http://www.openclinica.org/license
 * copyright 2003-2005 Akaza Research
 */
package org.akaza.openclinica.control.managestudy;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import core.org.akaza.openclinica.bean.core.Role;
import core.org.akaza.openclinica.bean.core.Status;
import core.org.akaza.openclinica.bean.managestudy.EventDefinitionCRFBean;
import core.org.akaza.openclinica.bean.managestudy.StudyEventDefinitionBean;
import core.org.akaza.openclinica.bean.service.StudyParamsConfig;
import core.org.akaza.openclinica.bean.submit.CRFVersionBean;
import core.org.akaza.openclinica.bean.submit.FormLayoutBean;
import core.org.akaza.openclinica.dao.hibernate.StudyDao;
import core.org.akaza.openclinica.dao.service.StudyConfigService;
import core.org.akaza.openclinica.domain.datamap.Study;
import core.org.akaza.openclinica.domain.datamap.StudyParameterValue;
import org.akaza.openclinica.config.StudyParamNames;
import org.akaza.openclinica.control.core.SecureController;
import org.akaza.openclinica.control.form.FormProcessor;
import org.akaza.openclinica.control.form.Validator;
import core.org.akaza.openclinica.core.form.StringUtil;
import core.org.akaza.openclinica.dao.managestudy.EventDefinitionCRFDAO;
import core.org.akaza.openclinica.dao.managestudy.StudyEventDefinitionDAO;
import core.org.akaza.openclinica.dao.service.StudyParameterValueDAO;
import core.org.akaza.openclinica.dao.submit.CRFVersionDAO;
import core.org.akaza.openclinica.dao.submit.FormLayoutDAO;
import core.org.akaza.openclinica.domain.SourceDataVerification;
import org.akaza.openclinica.view.Page;
import core.org.akaza.openclinica.web.InsufficientPermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author jxu
 *
 * @version CVS: $Id: UpdateSubStudyServlet.java,v 1.7 2005/07/05 21:55:58 jxu
 *          Exp $
 */
public class UpdateSubStudyServlet extends SecureController {
    private Logger logger=LoggerFactory.getLogger(getClass().getName());
    public static final String INPUT_START_DATE = "startDate";
    public static final String INPUT_VER_DATE = "protocolDateVerification";
    public static final String INPUT_END_DATE = "endDate";
    public static Study parentStudy;

    /**
     * *
     */
    @Override
    public void mayProceed() throws InsufficientPermissionException {
        checkStudyLocked(Page.SITE_LIST_SERVLET, respage.getString("current_study_locked"));
        if (ub.isSysAdmin()) {
            return;
        }
        if (currentRole.getRole().equals(Role.STUDYDIRECTOR) || currentRole.getRole().equals(Role.COORDINATOR)) {
            return;
        }

        addPageMessage(respage.getString("no_have_correct_privilege_current_study") + respage.getString("change_study_contact_sysadmin"));
        throw new InsufficientPermissionException(Page.STUDY_LIST, resexception.getString("not_study_director"), "1");

    }

    @Override
    public void processRequest() throws Exception {

        Study study = (Study) session.getAttribute("newStudy");
        parentStudy = (Study) getStudyDao().findByPK(study.checkAndGetParentStudyId());

        logger.info("study from session:" + study.getName() + "\n" + study.getDateCreated() + "\n");
        String action = request.getParameter("action");

        if (StringUtil.isBlank(action)) {
            request.setAttribute("facRecruitStatusMap", CreateStudyServlet.facRecruitStatusMap);
            request.setAttribute("statuses", Status.toStudyUpdateMembersList());
            FormProcessor fp = new FormProcessor(request);
            logger.info("start date:" + study.getDatePlannedEnd());
            if (study.getDatePlannedEnd() != null) {
                fp.addPresetValue(INPUT_END_DATE, local_df.format(study.getDatePlannedEnd()));
            }
            if (study.getDatePlannedStart() != null) {
                fp.addPresetValue(INPUT_START_DATE, local_df.format(study.getDatePlannedStart()));
            }
            if (study.getProtocolDateVerification() != null) {
                fp.addPresetValue(INPUT_VER_DATE, local_df.format(study.getProtocolDateVerification()));
            }

            setPresetValues(fp.getPresetValues());
            forwardPage(Page.UPDATE_SUB_STUDY);
        } else {
            if ("confirm".equalsIgnoreCase(action)) {
                confirmStudy();
                // issue 3348
                // } else if ("submit".equalsIgnoreCase(action)) {
                // submitStudy();

            }
        }
    }

    /**
     * Validates the first section of study and save it into study bean * *
     *
     * @param request
     * @param response
     * @throws Exception
     */
    private void confirmStudy() throws Exception {
        Validator v = new Validator(request);
        FormProcessor fp = new FormProcessor(request);

        if (parentStudy.getStatus().equals(Status.LOCKED)) {
            if (fp.getInt("statusId") != Status.LOCKED.getId()) {
                Validator.addError(errors, "statusId", respage.getString("study_locked_site_status_locked"));
            }
        }
        // else if (parentStudy.getStatus().equals(Status.FROZEN)) {
        // if (fp.getInt("statusId") != Status.AVAILABLE.getId()) {
        // Validator.addError(errors, "statusId",
        // respage.getString("study_locked_site_status_frozen"));
        // }
        // }

        Study study = createStudyBean();
        session.setAttribute("newStudy", study);

        if (errors.isEmpty()) {
            logger.info("no errors");
            // forwardPage(Page.CONFIRM_UPDATE_SUB_STUDY);
            submitStudy();
        } else {

            Study studyCheck = (Study) session.getAttribute("newStudy");
            parentStudy = (Study) getStudyDao().findByPK(studyCheck.checkAndGetParentStudyId());
            StudyParameterValueDAO spvdao = new StudyParameterValueDAO(sm.getDataSource());
            String participateFormStatus = spvdao.findByHandleAndStudy(parentStudy.getStudyId(), "participantPortal").getValue();
            request.setAttribute("participateFormStatus", participateFormStatus);

            logger.info("has validation errors");
            fp.addPresetValue(INPUT_START_DATE, fp.getString(INPUT_START_DATE));
            fp.addPresetValue(INPUT_VER_DATE, fp.getString(INPUT_VER_DATE));
            fp.addPresetValue(INPUT_END_DATE, fp.getString(INPUT_END_DATE));

            setPresetValues(fp.getPresetValues());
            request.setAttribute("formMessages", errors);
            request.setAttribute("facRecruitStatusMap", CreateStudyServlet.facRecruitStatusMap);
            request.setAttribute("statuses", Status.toStudyUpdateMembersList());
            forwardPage(Page.UPDATE_SUB_STUDY);
        }

    }

    /**
     * Constructs study bean from reques * *
     *
     * @param request
     * @return
     */
    private Study createStudyBean() {
        FormProcessor fp = new FormProcessor(request);
        Study study = (Study) session.getAttribute("newStudy");
        study.setName(fp.getString("name"));
        study.setUniqueIdentifier(fp.getString("uniqueProId"));
        study.setSecondaryIdentifier(fp.getString("secondProId"));
        study.setSummary(fp.getString("description"));
        study.setPrincipalInvestigator(fp.getString("prinInvestigator"));
        study.setExpectedTotalEnrollment(fp.getInt("expectedTotalEnrollment"));

        if (!StringUtil.isBlank(fp.getString("startDate")))
            study.setDatePlannedStart(fp.getDate("startDate"));
        else
            study.setDatePlannedStart(null);
        if (!StringUtil.isBlank(fp.getString("endDate")))
            study.setDatePlannedEnd(fp.getDate("endDate"));
        else
            study.setDatePlannedEnd(null);
        if (!StringUtil.isBlank(fp.getString(INPUT_VER_DATE)))
            study.setProtocolDateVerification(fp.getDate(INPUT_VER_DATE));
        else
            study.setProtocolDateVerification(null);

        study.setFacilityCity(fp.getString("facCity"));
        study.setFacilityContactDegree(fp.getString("facConDrgree"));
        study.setFacilityName(fp.getString("facName"));
        study.setFacilityContactEmail(fp.getString("facConEmail"));
        study.setFacilityContactPhone(fp.getString("facConPhone"));
        study.setFacilityContactName(fp.getString("facConName"));
        study.setFacilityContactDegree(fp.getString("facConDegree"));
        study.setFacilityCountry(fp.getString("facCountry"));
        study.setFacilityRecruitmentStatus(fp.getString("facRecStatus"));
        study.setFacilityState(fp.getString("facState"));
        study.setFacilityZip(fp.getString("facZip"));
        // study.setStatusId(fp.getInt("statusId"));
        study.setStatus(core.org.akaza.openclinica.domain.Status.getByCode(fp.getInt("statusId")));

        StudyConfigService scs = new StudyConfigService(sm.getDataSource());
        scs.updateOrCreateSpv(study, StudyParamNames.INTERVIEWER_NAME_REQUIRED, fp.getString(StudyParamNames.INTERVIEWER_NAME_REQUIRED));
        scs.updateOrCreateSpv(study, StudyParamNames.INTERVIEWER_NAME_DEFAULT, fp.getString(StudyParamNames.INTERVIEWER_NAME_DEFAULT));
        scs.updateOrCreateSpv(study, StudyParamNames.INTERVIEW_DATE_REQUIRED, fp.getString(StudyParamNames.INTERVIEW_DATE_REQUIRED));
        scs.updateOrCreateSpv(study, StudyParamNames.INTERVIEW_DATE_DEFAULT, fp.getString(StudyParamNames.INTERVIEW_DATE_DEFAULT));

        List<StudyParameterValue> spvList = study.getStudyParameterValues();
        if(spvList != null){
            for(StudyParameterValue spv : spvList){
                String value = fp.getString(spv.getStudyParameter().getHandle());
                if(value != null){
                    spv.setValue(value);
                }
            }
        }

        return study;

    }

    private void submitSiteEventDefinitions(Study site) throws MalformedURLException {
        FormProcessor fp = new FormProcessor(request);
        Validator v = new Validator(request);
        HashMap<String, Boolean> changes = new HashMap<String, Boolean>();
        HashMap<String, Boolean> changeStatus = (HashMap<String, Boolean>) session.getAttribute("changed");

        ArrayList<StudyEventDefinitionBean> seds = new ArrayList<StudyEventDefinitionBean>();

        ArrayList<EventDefinitionCRFBean> defCrfs = new ArrayList<EventDefinitionCRFBean>();
        StudyEventDefinitionDAO sedDao = new StudyEventDefinitionDAO(sm.getDataSource());
        CRFVersionDAO cvdao = new CRFVersionDAO(sm.getDataSource());
        FormLayoutDAO fldao = new FormLayoutDAO(sm.getDataSource());

        Study parentStudyBean;
        if (!site.isSite()) {
            parentStudyBean = site;
        } else {
            parentStudyBean = (Study) getStudyDao().findByPK(site.getStudy().getStudyId());
        }
        EventDefinitionCRFDAO edcdao = new EventDefinitionCRFDAO(sm.getDataSource());
        ArrayList<EventDefinitionCRFBean> eventDefCrfList = (ArrayList<EventDefinitionCRFBean>) edcdao
                .findAllActiveSitesAndStudiesPerParentStudy(parentStudyBean.getStudyId());

        ArrayList<EventDefinitionCRFBean> toBeCreatedEventDefBean = new ArrayList<>();
        ArrayList<EventDefinitionCRFBean> toBeUpdatedEventDefBean = new ArrayList<>();
        ArrayList<EventDefinitionCRFBean> edcsInSession = new ArrayList<EventDefinitionCRFBean>();
        boolean changestate = false;
        seds = (ArrayList<StudyEventDefinitionBean>) session.getAttribute("definitions");

        StudyParameterValueDAO spvdao = new StudyParameterValueDAO(sm.getDataSource());
        String participateFormStatus = spvdao.findByHandleAndStudy(parentStudyBean.getStudyId(), "participantPortal").getValue();
        if (participateFormStatus.equals("enabled"))
            baseUrl();
        request.setAttribute("participateFormStatus", participateFormStatus);

        for (StudyEventDefinitionBean sed : seds) {

            ArrayList<EventDefinitionCRFBean> edcs = sed.getCrfs();
            int start = 0;
            for (EventDefinitionCRFBean edcBean : edcs) {

                int edcStatusId = edcBean.getStatus().getId();
                if (edcStatusId == 5 || edcStatusId == 7) {
                } else {
                    String order = start + "-" + edcBean.getId();
                    int defaultVersionId = fp.getInt("defaultVersionId" + order);
                    String requiredCRF = fp.getString("requiredCRF" + order);
                    String doubleEntry = fp.getString("doubleEntry" + order);
                    String electronicSignature = fp.getString("electronicSignature" + order);
                    String hideCRF = fp.getString("hideCRF" + order);

                    String participantForm = fp.getString("participantForm" + order);
                    String allowAnonymousSubmission = fp.getString("allowAnonymousSubmission" + order);
                    String submissionUrl = fp.getString("submissionUrl" + order);
                    String offline = fp.getString("offline" + order);

                    int sdvId = fp.getInt("sdvOption" + order);
                    ArrayList<String> selectedVersionIdList = fp.getStringArray("versionSelection" + order);
                    int selectedVersionIdListSize = selectedVersionIdList.size();
                    String selectedVersionIds = "";
                    if (selectedVersionIdListSize > 0) {
                        for (String id : selectedVersionIdList) {
                            selectedVersionIds += id + ",";
                        }
                        selectedVersionIds = selectedVersionIds.substring(0, selectedVersionIds.length() - 1);
                    }
                    String sdvOption = fp.getString("sdvOption" + order);

                    boolean changed = false;

                    if (changeStatus != null && changeStatus.get(sed.getId() + "-" + edcBean.getId()) != null) {
                        changed = changeStatus.get(sed.getId() + "-" + edcBean.getId());
                        edcBean.setSubmissionUrl(submissionUrl);
                    }

                    boolean isRequired = !StringUtil.isBlank(requiredCRF) && "yes".equalsIgnoreCase(requiredCRF.trim()) ? true : false;
                    boolean isDouble = !StringUtil.isBlank(doubleEntry) && "yes".equalsIgnoreCase(doubleEntry.trim()) ? true : false;
                    boolean hasPassword = !StringUtil.isBlank(electronicSignature) && "yes".equalsIgnoreCase(electronicSignature.trim()) ? true : false;
                    boolean isHide = !StringUtil.isBlank(hideCRF) && "yes".equalsIgnoreCase(hideCRF.trim()) ? true : false;
                    logger.debug("crf name : {}" ,edcBean.getCrfName());
                    logger.debug("submissionUrl: {}", submissionUrl);

                    if (edcBean.getParentId() > 0) {
                        int dbDefaultVersionId = edcBean.getDefaultVersionId();
                        if (defaultVersionId != dbDefaultVersionId) {
                            changed = true;
                            FormLayoutBean defaultVersion = (FormLayoutBean) fldao.findByPK(defaultVersionId);
                            edcBean.setDefaultVersionId(defaultVersionId);
                            edcBean.setDefaultVersionName(defaultVersion.getName());
                        }
                        if (isRequired != edcBean.isRequiredCRF()) {
                            changed = true;
                            edcBean.setRequiredCRF(isRequired);
                        }
                        if (isDouble != edcBean.isDoubleEntry()) {
                            changed = true;
                            edcBean.setDoubleEntry(isDouble);
                        }
                        if (hasPassword != edcBean.isElectronicSignature()) {
                            changed = true;
                            edcBean.setElectronicSignature(hasPassword);
                        }
                        if (isHide != edcBean.isHideCrf()) {
                            changed = true;
                            edcBean.setHideCrf(isHide);
                        }
                        if (!submissionUrl.equals(edcBean.getSubmissionUrl())) {
                            changed = true;
                            edcBean.setSubmissionUrl(submissionUrl);
                        }
                        if (!StringUtil.isBlank(selectedVersionIds) && !selectedVersionIds.equals(edcBean.getSelectedVersionIds())) {
                            changed = true;
                            String[] ids = selectedVersionIds.split(",");
                            ArrayList<Integer> idList = new ArrayList<Integer>();
                            for (String id : ids) {
                                idList.add(Integer.valueOf(id));
                            }
                            edcBean.setSelectedVersionIdList(idList);
                            edcBean.setSelectedVersionIds(selectedVersionIds);
                        }
                        if (sdvId > 0 && sdvId != edcBean.getSourceDataVerification().getCode()) {
                            changed = true;
                            edcBean.setSourceDataVerification(SourceDataVerification.getByCode(sdvId));
                        }

                        if (changed) {
                            edcBean.setUpdater(ub);
                            edcBean.setUpdatedDate(new Date());
                            logger.debug("update for site");
                            toBeUpdatedEventDefBean.add(edcBean);
                            // edcdao.update(edcBean);
                        }
                    } else {
                        // only if definition-crf has been modified, will it be
                        // saved for the site
                        int defaultId = defaultVersionId > 0 ? defaultVersionId : edcBean.getDefaultVersionId();
                        int dbDefaultVersionId = edcBean.getDefaultVersionId();
                        if (defaultId == dbDefaultVersionId) {
                            if (isRequired == edcBean.isRequiredCRF()) {
                                if (isDouble == edcBean.isDoubleEntry()) {
                                    if (hasPassword == edcBean.isElectronicSignature()) {
                                        if (isHide == edcBean.isHideCrf()) {
                                            if (submissionUrl.equals("")) {

                                                if (selectedVersionIdListSize > 0) {
                                                    if (selectedVersionIdListSize == edcBean.getVersions().size()) {
                                                        if (sdvId > 0) {
                                                            if (sdvId != edcBean.getSourceDataVerification().getCode()) {
                                                                changed = true;
                                                            }
                                                        }
                                                    } else {
                                                        changed = true;
                                                    }
                                                }
                                            } else {
                                                changed = true;
                                            }
                                        } else {
                                            changed = true;
                                        }
                                    } else {
                                        changed = true;
                                    }
                                } else {
                                    changed = true;
                                }
                            } else {
                                changed = true;
                            }
                        } else {
                            changed = true;
                        }

                        if (changed) {
                            CRFVersionBean defaultVersion = (CRFVersionBean) cvdao.findByPK(defaultId);
                            edcBean.setDefaultVersionId(defaultId);
                            edcBean.setDefaultVersionName(defaultVersion.getName());
                            edcBean.setRequiredCRF(isRequired);
                            edcBean.setDoubleEntry(isDouble);
                            edcBean.setElectronicSignature(hasPassword);
                            edcBean.setHideCrf(isHide);
                            edcBean.setSubmissionUrl(submissionUrl);

                            if (selectedVersionIdListSize > 0 && selectedVersionIdListSize != edcBean.getVersions().size()) {
                                String[] ids = selectedVersionIds.split(",");
                                ArrayList<Integer> idList = new ArrayList<Integer>();
                                for (String id : ids) {
                                    idList.add(Integer.valueOf(id));
                                }
                                edcBean.setSelectedVersionIdList(idList);
                                edcBean.setSelectedVersionIds(selectedVersionIds);
                            }
                            if (sdvId > 0 && sdvId != edcBean.getSourceDataVerification().getCode()) {
                                edcBean.setSourceDataVerification(SourceDataVerification.getByCode(sdvId));
                            }
                            // edcBean.setParentId(edcBean.getId());
                            edcBean.setStudyId(site.getStudyId());
                            edcBean.setUpdater(ub);
                            edcBean.setUpdatedDate(new Date());
                            logger.debug("create for the site");
                            toBeCreatedEventDefBean.add(edcBean);
                            // edcdao.create(edcBean);
                        }
                    }
                    ++start;
                    changes.put(sed.getId() + "-" + edcBean.getId(), changed);
                }
                edcsInSession.add(edcBean);

            }
            sed.setPopulated(false);
            eventDefCrfList = validateSubmissionUrl(edcsInSession, eventDefCrfList, v, sed);
            edcsInSession.clear();

        }
        errors = v.validate();

        if (!errors.isEmpty()) {
            logger.info("has errors");
            Study study = createStudyBean();
            session.setAttribute("newStudy", study);
            request.setAttribute("formMessages", errors);
            session.setAttribute("changed", changes);
            forwardPage(Page.UPDATE_SUB_STUDY);
        } else {
            for (EventDefinitionCRFBean toBeCreated : toBeCreatedEventDefBean) {
                toBeCreated.setParentId(toBeCreated.getId());
                edcdao.create(toBeCreated);
            }
            for (EventDefinitionCRFBean toBeUpdated : toBeUpdatedEventDefBean) {
                edcdao.update(toBeUpdated);
            }

        }
    }

    /**
     * Inserts the new study into databa *
     * 
     * @throws MalformedURLException
     *             *
     */
    private void submitStudy() throws MalformedURLException {
        Study study = (Study) session.getAttribute("newStudy");
        submitSiteEventDefinitions(study);

        // session.removeAttribute("newStudy");
        // session.removeAttribute("parentName");
        // session.removeAttribute("definitions");
        // session.removeAttribute("sdvOptions");
        addPageMessage(respage.getString("the_site_has_been_updated_succesfully"));
        String fromListSite = (String) session.getAttribute("fromListSite");
        if (fromListSite != null && fromListSite.equals("yes")) {
            // session.removeAttribute("fromListSite");
            forwardPage(Page.SITE_LIST_SERVLET);
        } else {
            // session.removeAttribute("fromListSite");
            forwardPage(Page.ERROR);
        }

    }

    @Override
    protected String getAdminServlet() {
        if (ub.isSysAdmin()) {
            return SecureController.ADMIN_SERVLET_CODE;
        } else {
            return "";
        }
    }

    public ArrayList<EventDefinitionCRFBean> validateSubmissionUrl(ArrayList<EventDefinitionCRFBean> edcsInSession,
            ArrayList<EventDefinitionCRFBean> eventDefCrfList, Validator v, StudyEventDefinitionBean sed) {
        for (int i = 0; i < edcsInSession.size(); i++) {
            String order = i + "-" + edcsInSession.get(i).getId();
            v.addValidation("submissionUrl" + order, Validator.NO_SPACES_ALLOWED);
            EventDefinitionCRFBean sessionBean = null;
            boolean isExist = false;
            for (EventDefinitionCRFBean eventDef : eventDefCrfList) {
                sessionBean = edcsInSession.get(i);
                logger.debug("iter:           {} --db: {}" , eventDef.getId() , eventDef.getSubmissionUrl());
                logger.debug("edcsInSession:  {}--session: {}", sessionBean.getId() , sessionBean.getSubmissionUrl());
                if (sessionBean.getSubmissionUrl().trim().equals("") || sessionBean.getSubmissionUrl().trim() == null) {
                    break;
                } else {
                    if ((eventDef.getSubmissionUrl().trim().equalsIgnoreCase(sessionBean.getSubmissionUrl().trim())
                            && (eventDef.getId() != sessionBean.getId()))
                            || (eventDef.getSubmissionUrl().trim().equalsIgnoreCase(sessionBean.getSubmissionUrl().trim())
                                    && (eventDef.getId() == sessionBean.getId()) && eventDef.getId() == 0)) {
                        v.addValidation("submissionUrl" + order, Validator.SUBMISSION_URL_NOT_UNIQUE);
                        sed.setPopulated(true);
                        logger.debug("Duplicate *****************");
                        isExist = true;
                        break;
                    } else if (eventDef.getSubmissionUrl().trim().equalsIgnoreCase(sessionBean.getSubmissionUrl().trim())
                            && (eventDef.getId() == sessionBean.getId())) {
                        logger.debug("Not Duplicate **********");
                        isExist = true;
                        break;
                    }
                }
            }
            if (!isExist) {
                eventDefCrfList.add(sessionBean);
            }
        }
        return eventDefCrfList;
    }

}
