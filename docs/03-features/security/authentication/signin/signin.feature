@auth @signin
Feature: User sign in
  As a registered user
  I want to sign in with my credentials or a social provider
  So that I can access the application

  @web
  Background:
    Given the I can access the the web app
    And I am on the "Sign in" screen

  @mobile
  Background:
    Given the app is installed and launched
    And I am on the "Sign in" screen

  Rule: Email + password sign in
    Scenario: Successful sign in
      Given I have a valid account
      When I enter a valid email and a valid password
      And I press "Sign in"
      Then I should be redirected to the Home screen

    Scenario Outline: Invalid credentials
      When I enter "<email>" and "<password>"
      And I press "Sign in"
      Then I should see an authentication error message
      Examples:
        | email                 | password     |
        | valid@domain.com      | wrongPass123 |
        | wrong@domain.com      | Correct#123  |

  Rule: Social sign in
    Scenario: Sign in with Google (existing user)
      Given I have an account created via Google OAuth
      When I press "Continue with Google"
      Then the Google OAuth flow should start
      And I authenticate with Google
      And upon success I should be redirected to the Home screen
      And I should be signed in

    Scenario: Sign in with Google (new user)
      Given I do not have an account
      When I press "Continue with Google"
      Then the Google OAuth flow should start
      And I authenticate with Google
      And a new account should be created with my Google email
      And upon success I should be redirected to the Home screen
      And I should be signed in

  Rule: Session persistence
    Scenario: Keep me signed in
      Given the "Keep me signed in" option is enabled
      When I sign in successfully
      Then my session should persist after closing and reopening the app
