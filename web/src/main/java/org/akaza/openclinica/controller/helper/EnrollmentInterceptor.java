package org.akaza.openclinica.controller.helper;

import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.control.SpringServletAccess;
import org.akaza.openclinica.core.SessionManager;
import org.akaza.openclinica.dao.hibernate.StudyDao;
import org.akaza.openclinica.dao.hibernate.StudyParameterValueDao;
import org.akaza.openclinica.dao.login.UserAccountDAO;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.akaza.openclinica.dao.managestudy.StudySubjectDAO;
import org.akaza.openclinica.dao.service.StudyParameterValueDAO;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.i18n.core.LocaleResolver;
import org.akaza.openclinica.i18n.util.ResourceBundleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.util.Locale;

/**
 * An "interceptor" class that sets up a UserAccount and stores it in the Session, before
 * another class is initialized and potentially uses that UserAccount.
 */
@EnableWebMvc
@Configuration
public class EnrollmentInterceptor extends HandlerInterceptorAdapter {

    public static final String USER_BEAN_NAME = "userBean";

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;
    @Autowired
    private StudyParameterValueDao studyParameterValueDao;

    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) throws Exception {

        if (httpServletRequest.getAttribute("enrollmentCapped") == null && httpServletRequest.getRequestURI().contains(httpServletRequest.getContextPath() + "/pages/")) {

            boolean isCapped = isEnrollmentCapped(httpServletRequest);
            httpServletRequest.setAttribute("enrollmentCapped", isCapped);
        }
        return true;
    }

    private boolean isEnrollmentCapEnforced(HttpServletRequest httpServletRequest, StudyBean currentStudy) {
        String enrollmentCapStatus = null;
        if (currentStudy.getParentStudyId() != 0) {
            enrollmentCapStatus = studyParameterValueDao.findByStudyIdParameter(currentStudy.getParentStudyId(), "enforceEnrollmentCap").getValue();
        } else {

            enrollmentCapStatus = studyParameterValueDao.findByStudyIdParameter(currentStudy.getId(), "enforceEnrollmentCap").getValue();
        }
        boolean capEnforced = Boolean.valueOf(enrollmentCapStatus);
        return capEnforced;
    }

    protected boolean isEnrollmentCapped(HttpServletRequest httpServletRequest) {

        boolean capIsOn;
        HttpSession session = httpServletRequest.getSession();
        StudyBean currentStudy = (StudyBean) session.getAttribute("study");

        if (currentStudy != null) {
            if (currentStudy.getStatus() != null && currentStudy.getStatus().isAvailable()) {
                capIsOn = isEnrollmentCapEnforced(httpServletRequest, currentStudy);

                StudySubjectDAO studySubjectDAO = new StudySubjectDAO(dataSource);
                int numberOfSubjects = studySubjectDAO.getCountofActiveStudySubjects();

                StudyDAO studyDAO = new StudyDAO(dataSource);
                StudyBean sb;
                if (currentStudy.getParentStudyId() != 0) {
                    sb = (StudyBean) studyDAO.findByPK(currentStudy.getParentStudyId());
                } else {
                    sb = (StudyBean) studyDAO.findByPK(currentStudy.getId());
                }
                int expectedTotalEnrollment = sb.getExpectedTotalEnrollment();

                if (numberOfSubjects >= expectedTotalEnrollment && capIsOn)
                    return true;
                else
                    return false;
            }
        }

        // If there is no current study it shouldn't matter if the variable is set to false.
        return false;
    }
}
