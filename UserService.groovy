package com.thestreet.cms

import com.google.common.base.Strings
import com.thestreet.cms.contributors.controller.helpers.RegisterCommand
import com.thestreet.cms.contributors.controller.helpers.UserProfileCommand
import com.thestreet.cms.domain.Department
import com.thestreet.cms.domain.Expertise
import com.thestreet.cms.domain.RegistrationInfo
import com.thestreet.cms.domain.Role
import com.thestreet.cms.domain.Site
import com.thestreet.cms.domain.User
import com.thestreet.cms.domain.UserRole
import com.thestreet.cms.domain.UserSite
import com.thestreet.cms.utils.ApprovalStatus
import grails.plugin.springsecurity.SpringSecurityUtils

import javax.crypto.*
import javax.xml.bind.DatatypeConverter
import grails.transaction.Transactional

import java.security.MessageDigest

@Transactional
class UserService {

    String generateEmailVerificationCode(User user) {
        if(user != null && !Strings.isNullOrEmpty(user.getEmail()) && user.emailVerificationCodeDate != null){
            try {
                String message = String.format("%s:%s", user.getEmail(), user.emailVerificationCodeDate.getTime());
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(message.getBytes("UTF-8"));

                //convert byte array to Hexadecimal String
                StringBuilder sb = new StringBuilder(2*hash.length);
                for(byte b : hash){
                    sb.append(String.format("%02x", b&0xff));
                }

                return sb.toString();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to generate verification code", e);
            }
        }

        throw new IllegalStateException("User account is not in a valid state for verification code generation");

    }

    /**
     * Created by Tony on 11/20/14.
     * This is service for UsersController to update a user domain's information
     *
     *  @form a form submitted for updating
     */

    User saveProfile(UserProfileCommand form){

        try{

            User profile = User.findById(form.id)

            profile.firstName = form.firstName
            profile.lastName = form.lastName
            profile.title = form.title
            profile.disclosure = form.disclosure
            profile.metaDescriptionOverride = form.seoDescription
            profile.metaPageTitleOverride = form.seoPageTitle
            profile.websiteUrl = form.websiteUrl
            profile.profileImageUrl = form.profileImageUrl
            profile.biography = form.biography
            profile.disclosure = form.disclosure

            if (SpringSecurityUtils.ifAnyGranted("Administrator")) {
                profile.isModerated = form.isModerated
                profile.isExternalContributor = form.isExternalContributor
            }

            return profile.save(failOnError: true, flush: true)
        }
        catch(Exception e){
            log.error("Unable to save user account ", e)
        }

        return null
    }

    def submitRegistration(RegisterCommand registrant) {
        def now = new Date();
        def editorialDpt = Department.findByName("Editorial")
        def account = new User(
                firstName: registrant.firstName,
                lastName:  registrant.lastName,
                title: "Contributor",
                username:  registrant.email,
                password:  registrant.password,
                websiteUrl: registrant.websiteUrl,
                created: now,
                lastModified: now,
                biography: registrant.biography,
                email: registrant.email,
                emailVerified: false,
                emailVerificationCodeDate: new Date(),
                approvalStatus: ApprovalStatus.PENDING,
                accountExpired: false,
                accountLocked: false,
                passwordExpired: false,
                isExternalContributor: false,
                isModerated: true,
                active: true
        )

        if(editorialDpt != null){
            account.department = editorialDpt
        }

        account.emailVerificationCode = generateEmailVerificationCode(account)

        def registrationInfo = new RegistrationInfo(
                qualification: registrant.qualification,
                otherExpertise: registrant.otherExpertise,
                submitted: new Date(),
                status: account.approvalStatus,
                user: account
        )

        registrationInfo.save()

        account.registration = registrationInfo

        if(registrant.expertise?.size() > 0){
            def items = []
            items.addAll(registrant.expertise)

            Expertise.findAllByIdInList(items)?.each { expertise ->
                account.addToExpertise(expertise)
            }
        }

        account.save(failOnError: true)

        def prospectRole = Role.findByName("Prospect")
        Site tscSite = Site.findByCode("TSC");

        if(tscSite != null){
           new UserSite(user: account, site: tscSite, active: true, searchable: true).save()
        }

        new UserRole(user: account, role:prospectRole).save()

        return account
    }

    Boolean approveRegistrant(User registrant){
        if(registrant != null){
            try {
                User.withTransaction {
                    if(registrant.approvalStatus == ApprovalStatus.APPROVED){
                        return true;
                    }
                    def editorRole = Role.findByName("Author")
                    registrant.roles.each{
                        if("prospect".equalsIgnoreCase(it.name)){
                            UserRole.remove(registrant, it)
                        }
                    }

                    if(editorRole != null){
                        if(registrant.emailVerified == null){
                            registrant.emailVerified = false
                        }

                        if(registrant.emailVerificationCode == null){
                            registrant.emailVerificationCodeDate = new Date()
                            registrant.emailVerificationCode = generateEmailVerificationCode(registrant)
                        }

                        if(registrant.department == null){
                            def department = Department.findByName("Editorial")
                            if(department != null){
                                registrant.department = department
                            }
                        }

                        registrant.approvalStatus = ApprovalStatus.APPROVED
                        registrant.approvalDate = new Date();

                        registrant.registration?.status = ApprovalStatus.APPROVED

                        registrant.save( failOnError: true)

                        new UserRole(user: registrant, role:editorRole).save()
                        return true
                    }
                }
            } catch (Exception e) {
                log.error("Error while approving registration for ${registrant.id}", e)
            }
        }

        return false
    }

    Boolean declineRegistrant(User registrant) {
        if(registrant != null){
            try {
                User.withTransaction {
                    if(!registrant.active){
                        return true;
                    }

                    if(isProspect(registrant)){
                        if(registrant.emailVerified == null){
                            registrant.emailVerified = false
                        }

                        if(registrant.emailVerificationCode == null){
                            registrant.emailVerificationCodeDate = new Date()
                            registrant.emailVerificationCode = generateEmailVerificationCode(registrant)
                        }

                        if(registrant.department == null){
                            def department = Department.findByName("Editorial")
                            if(department != null){
                                registrant.department = department
                            }
                        }

                        registrant.approvalStatus = ApprovalStatus.DECLINED;
                        registrant.approvalDate = new Date();

                        registrant.registration?.status = ApprovalStatus.DECLINED

                        registrant.save( failOnError: true)
                        return true
                    }
                }
            } catch (Exception e) {
                log.error("Error while declining registration for ${registrant.id}", e)
            }
        }

        return false
    }

    boolean setModeratedPublishingStatus(Long id, boolean enabled){
        return setModeratedPublishingStatus(User.load(id),enabled)
    }

    boolean setModeratedPublishingStatus(User user, boolean enabled){
        boolean result = false

        if(user != null){
            user.isModerated = enabled
            user.save()

            result = true
        }
        return result
    }

    boolean isProspect(def user) {
        return user?.roles?.findAll{ "prospect".equalsIgnoreCase(it.name)}?.size() > 0
    }

    def findApplicants(def max = -1) {
        def results = [
                'pending':[],
                'approved':[],
                'declined':[]
        ]

        ApprovalStatus.values().each {approvalStatus->
            results."${approvalStatus.name().toLowerCase()}".addAll(findApplicants(approvalStatus, max))
        }

        return results
    }

    def findApplicants(ApprovalStatus status, def max = -1) {
        def retval = []

        try {
            def args = [sort: 'created', order: 'DESC']
            if(max > 0){
                args.max = max
            }

            User.findAllByApprovalStatus(status, args).each { account ->
                if (account.registration == null) {
                    def reg = new RegistrationInfo(
                            user: account,
                            qualification: ".",
                            submitted: account.created,
                            status: account.approvalStatus)
                    reg.save()
                    account.registration = reg
                }

                if(status == ApprovalStatus.PENDING && (account.emailVerified == null || !account.emailVerified) ){
                    return
                }

                retval.add(account)
            }
        } catch (Exception e) {
            log.warn("Unable to find registrations by status.", e)
        }

        return retval
    }

    def verifyAccount(String code){
        User user = User.findByEmailVerificationCode(code);
        if(user != null){
            User.withTransaction {
                user.emailVerified = true;
                user.save()
            }

        }
    }

    String generateApiToken(Long id){

        return generateApiToken(User.get(id));
    }

    String generateApiToken(User user){
        if(user != null){
            try {
                def secretKey = KeyGenerator.getInstance("HmacSHA256").generateKey()
                String apiToken = DatatypeConverter.printBase64Binary(secretKey.encoded)
                user.apiToken = apiToken
                user.save()

                return apiToken;
            } catch (Exception e) {
                log.warn("Unable to generate api token for user ${user?.username}", e)
            }
        }

        return null
    }
}
