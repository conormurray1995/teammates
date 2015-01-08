package teammates.ui.controller;

import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackQuestionAttributes;
import teammates.common.datatransfer.FeedbackSessionAttributes;
import teammates.common.datatransfer.FeedbackSessionQuestionsBundle;
import teammates.common.datatransfer.InstructorAttributes;
import teammates.common.datatransfer.StudentAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.exception.UnauthorizedAccessException;
import teammates.common.util.Assumption;
import teammates.common.util.Const;
import teammates.common.util.HttpRequestHelper;
import teammates.logic.api.GateKeeper;


public class InstructorEditStudentFeedbackSaveAction extends FeedbackSubmissionEditSaveAction {
    
    StudentAttributes moderatedStudent;
    
    @Override
    protected void verifyAccesibleForSpecificUser() {
        InstructorAttributes instructor = logic.getInstructorForGoogleId(courseId, account.googleId);
        FeedbackSessionAttributes session = logic.getFeedbackSession(feedbackSessionName, courseId);
                
        new GateKeeper().verifyAccessible(instructor,
                session,
                false, moderatedStudent.section, 
                session.feedbackSessionName, 
                Const.ParamsNames.INSTRUCTOR_PERMISSION_MODIFY_SESSION_COMMENT_IN_SECTIONS);
        
    }
    
    @Override
    protected void setAdditionalParameters() {
        String moderatedStudentEmail = getRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_MODERATED_STUDENT);
        Assumption.assertPostParamNotNull(Const.ParamsNames.FEEDBACK_SESSION_MODERATED_STUDENT, moderatedStudentEmail);

        moderatedStudent = logic.getStudentForEmail(courseId, moderatedStudentEmail);
    }

    @Override
    protected void checkAdditionalConstraints() {
        // check the instructor did not submit responses to questions that he/she should not be able
        // to view during moderation
        
        InstructorAttributes instructor = logic.getInstructorForGoogleId(courseId, account.googleId);
        
        int numOfQuestionsToGet = data.bundle.questionResponseBundle.size();
        for (int questionIndx = 1; questionIndx <= numOfQuestionsToGet; questionIndx++) {
            String questionId = HttpRequestHelper.getValueFromParamMap(
                    requestParameters, 
                    Const.ParamsNames.FEEDBACK_QUESTION_ID + "-" + questionIndx);
            if (questionId == null) {
                // we do not throw an error if the question was not present on the page for instructors to edit
                continue;
            }
            FeedbackQuestionAttributes questionAttributes = data.bundle.getQuestionAttributes(questionId);
            
            if (questionAttributes == null){
                statusToUser.add("The feedback session or questions may have changed while you were submitting. Please check your responses to make sure they are saved correctly.");
                isError = true;
                log.warning("Question not found. (deleted or invalid id passed?) id: "+ questionId + " index: " + questionIndx);
                continue;
            }
            
            boolean isGiverVisibleToInstructors = questionAttributes.showGiverNameTo.contains(FeedbackParticipantType.INSTRUCTORS);
            boolean isRecipientVisibleToInstructors = questionAttributes.showRecipientNameTo.contains(FeedbackParticipantType.INSTRUCTORS);
            boolean isResponseVisibleToInstructors = questionAttributes.showResponsesTo.contains(FeedbackParticipantType.INSTRUCTORS);
            
            
            if (!isGiverVisibleToInstructors || !isRecipientVisibleToInstructors || !isResponseVisibleToInstructors) {
                isError = true;
                throw new UnauthorizedAccessException(
                        "Feedback session [" + feedbackSessionName + 
                        "] question [" + questionAttributes.getId() + "] is not accessible to instructor ["+ instructor.email + "]");
            }
            
        }
    }
    
    @Override
    protected void appendRespondant() {
        try {
            logic.addStudentRespondant(getUserEmailForCourse(), feedbackSessionName, courseId);
        } catch (InvalidParametersException | EntityDoesNotExistException e) {
            log.severe("Fail to append student respondant");
        }
    }

    @Override
    protected void removeRespondant() {
        try {
            logic.deleteStudentRespondant(getUserEmailForCourse(), feedbackSessionName, courseId);
        } catch (InvalidParametersException | EntityDoesNotExistException e) {
            log.severe("Fail to remove student respondant");
        }
    }

    @Override
    protected String getUserEmailForCourse() {
        return moderatedStudent.email;
    }
    
    @Override
    protected String getUserSectionForCourse() {
        return moderatedStudent.section;
    }

    @Override
    protected FeedbackSessionQuestionsBundle getDataBundle(String userEmailForCourse)
            throws EntityDoesNotExistException {
        return logic.getFeedbackSessionQuestionsBundleForStudent(
                feedbackSessionName, courseId, userEmailForCourse);
    }

    @Override
    protected void setStatusToAdmin() {
        statusToAdmin = "Instructor moderated student session<br>" +
                        "Instructor: " + account.email + "<br>" + 
                        "Moderated Student: " + moderatedStudent + "<br>" +
                        "Session Name: " + feedbackSessionName + "<br>" +
                        "Course ID: " + courseId;
    }

    @Override
    protected boolean isSessionOpenForSpecificUser(FeedbackSessionAttributes session) {
        // Feedback session closing date does not matter. Instructors can moderate at any time
        return true; 
    }

    @Override
    protected RedirectResult createSpecificRedirectResult() {
        RedirectResult result = createRedirectResult(Const.ActionURIs.INSTRUCTOR_EDIT_STUDENT_FEEDBACK_PAGE);
        
        result.responseParams.put(Const.ParamsNames.COURSE_ID, moderatedStudent.course);
        result.responseParams.put(Const.ParamsNames.FEEDBACK_SESSION_NAME, feedbackSessionName);
        result.responseParams.put(Const.ParamsNames.FEEDBACK_SESSION_MODERATED_STUDENT, moderatedStudent.email);
        
        return result;
    }
}