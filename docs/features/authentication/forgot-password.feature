@auth @forgot-password
Feature: User forgot password
  As a user who forgot their password
  I want to request a password reset and set a new password
  So that I can regain access to my account

  @web
  Background:
    Given I can access the web app
    And I am on the "Sign in" screen

  @mobile
  Background:
    Given the app is installed and launched
    And I am on the "Sign in" screen

  Rule: Password reset request flow
    Scenario: Successful password reset request
      Given I have a valid account with email "user@example.com"
      When I click "Forgot your password?"
      And I enter my email "user@example.com"
      And I press "Send reset link"
      Then I should see a success message
      And I should receive a password reset email

    Scenario: Email not found (security - generic success message)
      Given I do not have an account
      When I click "Forgot your password?"
      And I enter a non-existent email "nonexistent@example.com"
      And I press "Send reset link"
      Then I should see a success message
      And I should not receive a password reset email

    Scenario: Invalid email format
      When I click "Forgot your password?"
      And I enter an invalid email "invalid-email"
      And I press "Send reset link"
      Then I should see an email validation error

    Scenario: Rate limiting - too many requests
      Given I have a valid account with email "user@example.com"
      When I request password reset 4 times within 1 hour for email "user@example.com"
      Then the 4th request should be rate limited
      And I should still see a success message (security)

  Rule: Token validation and expiration
    Scenario: Reset password with valid token
      Given I have a valid password reset token
      When I click the reset link in my email
      And I am redirected to the reset password page
      And I enter a new valid password
      And I confirm the new password
      And I press "Reset password"
      Then my password should be updated
      And I should see a success message
      And I should be redirected to the sign in page

    Scenario: Reset password with expired token
      Given I have an expired password reset token
      When I click the reset link in my email
      And I am redirected to the reset password page
      And I enter a new password
      And I press "Reset password"
      Then I should see an error message about invalid or expired token

    Scenario: Reset password with already used token
      Given I have already used a password reset token
      When I try to use the same token again
      And I enter a new password
      And I press "Reset password"
      Then I should see an error message about invalid or expired token

    Scenario: Reset password with invalid token
      Given I have an invalid or tampered password reset token
      When I try to reset my password with the invalid token
      Then I should see an error message about invalid or expired token

  Rule: Password reset completion
    Scenario: Weak password validation
      Given I have a valid password reset token
      When I enter a weak password that doesn't meet requirements
      And I press "Reset password"
      Then I should see a password validation error
      And the password requirements should be shown

    Scenario: Password confirmation mismatch
      Given I have a valid password reset token
      When I enter a new password
      And I enter a different password in the confirmation field
      And I press "Reset password"
      Then I should see a password mismatch error

    Scenario: Successful password reset with strong password
      Given I have a valid password reset token
      When I enter a strong password that meets all requirements
      And I confirm the password
      And I press "Reset password"
      Then my password should be updated
      And I should see a success message
      And I should be able to sign in with the new password

  Rule: Edge cases
    Scenario: User account without password (OAuth users)
      Given I have an account created via OAuth without a password
      When I request a password reset for my email
      Then I should see a success message (security)
      And I should not receive a password reset email

    Scenario: Concurrent reset requests
      Given I have a valid account with email "user@example.com"
      When I request password reset twice in quick succession
      Then only the latest reset token should be valid
      And the previous token should be invalidated

    Scenario: Network timeout during reset request
      Given the network is slow or times out
      When I request a password reset
      Then I should see an appropriate error message
      And I can retry the request

    Scenario: Email delivery failure
      Given the email service is unavailable
      When I request a password reset
      Then I should still see a success message (security)
      And the system should log the email delivery failure

