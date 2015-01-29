package com.thestreet.cms.contributors.controller.api
import com.thestreet.cms.contributors.controller.UsersController
import com.thestreet.cms.contributors.controller.helpers.UserProfileCommand
import com.thestreet.cms.domain.User
import grails.test.spock.IntegrationSpec
/**
 * Created by Tony on 11/20/14.
 * This is an integration test case for UsersController, which test the save function.
 * An integration test differs from a unit test in that the Grails environment is loaded
 * for each test execution.
 *
 * The save function will take a profile(UserProofileCommand) as input, then update the user
 * information accordingly
 */

class UsersControllerSpec extends IntegrationSpec {
    def controller = new UsersController()

    UserProfileCommand createUserProfileCommand() {
        UserProfileCommand profile = new UserProfileCommand();
        profile.id = 1604404
        profile.username = "tony.wang"
        profile.firstName = "tony"
        profile.lastName = "wang"
        profile.email = "tony.wang@thestreet.com"
        profile.title = "SDE"
        profile.biography = "cute"

        profile.disclosure = "123"
        profile.seoDescription = "123"
        profile.seoPageTitle = "test"
        profile.websiteUrl = "http://thestreet.com"
        profile.profileImageUrl = "http://thestreet.com"

        profile.isModerated = true
        profile.isExternalContributor = false
        return profile
    }

    def setup() {
    }

    def cleanup() {
    }

    void "test UserService"() {
        when: "Generated a form/ profile, then update the user information by saveProfile() method "
            UserProfileCommand profile1 =  createUserProfileCommand()
            def previousTittle = User.findByFirstName("Tony").title
            System.out.println("previous tittle is " + previousTittle);

            User testUser1 = controller.userService.saveProfile(profile1);
            def currentTittle = User.findByFirstName("tony").title
            System.out.println("current tittle is " + currentTittle);

        then: "The form should be valid and user information(title) has been changed"
            profile1 != null
            profile1.validate() == true
            testUser1 != null
            previousTittle != currentTittle
    }

    void "test save() in UserController"() {
        when: "Generated a form/ profile, then update the user information by save() method "
            UserProfileCommand profile1 =  createUserProfileCommand()
            def previousId =  User.findByFirstName("Tony").id
            controller.save (profile1);
            def currentId =  User.findByFirstName("tony").id

        then: "The form should be valid, the information is updated but user's id should be unchanged"
            profile1 != null
            profile1.validate() == true
            User.findByFirstName("tony") != null
            previousId == currentId
    }
}
