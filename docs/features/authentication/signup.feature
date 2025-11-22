@auth @signup
Feature: User sign up
  As a new user
  I want to create an account with my name, email, and password
  So that I can access the application

  @web
  Background:
    Given I can access the web app
    And I am on the "Sign up" screen

  @mobile
  Background:
    Given the app is installed and launched
    And I am on the "Sign up" screen

  Rule: User must input name, email, and password
    Scenario: Successful sign up
      Given I am a new user
      When I enter a valid name, a valid email, and a valid password
      And I press "Sign up"
      Then I should be redirected to the Home screen
      And I should be signed in

    Scenario: Sign up with invalid email format
      When I enter a name and an invalid email format
      And I press "Sign up"
      Then I should see an email validation error message

    Scenario: Sign up with duplicate email
      Given a user with email "existing@example.com" already exists
      When I enter a name and email "existing@example.com"
      And I press "Sign up"
      Then I should see an error message that the email is already taken

  Rule: Email must be unique and valid
    Scenario: Email validation
      When I enter an invalid email format
      Then I should see an email validation error
      And the email field should be marked as invalid

  Rule: Password must be at least 8 characters with uppercase, lowercase, number and special character
    Scenario: Password validation - too short
      When I enter a password with less than 8 characters
      Then I should see a password validation error
      And the password requirements should show red indicators

    Scenario: Password validation - missing uppercase
      When I enter a password without uppercase letters
      Then I should see a password validation error
      And the password requirements should show red indicators

    Scenario: Password validation - missing lowercase
      When I enter a password without lowercase letters
      Then I should see a password validation error
      And the password requirements should show red indicators

    Scenario: Password validation - missing number
      When I enter a password without numbers
      Then I should see a password validation error
      And the password requirements should show red indicators

    Scenario: Password validation - missing special character
      When I enter a password without special characters
      Then I should see a password validation error
      And the password requirements should show red indicators

    Scenario: Password validation - valid password
      When I enter a password that meets all requirements
      Then the password requirements should show green indicators
      And I should be able to submit the form

    Scenario: Password validation - live validation display
      When I start typing a password
      Then the password requirements should be visible
      When I clear the password field
      Then the password requirements should be hidden

