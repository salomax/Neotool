@auth @logout
Feature: User logout
  As an authenticated user
  I want to sign out of my account
  So that I can securely end my session

  @web
  Background:
    Given I am signed in to the application
    And I can see the profile button in the header

  @mobile
  Background:
    Given I am signed in to the application
    And I can see the profile button in the header

  Rule: Profile menu access
    Scenario: Open profile menu
      When I click on the profile button
      Then I should see a dropdown menu with profile options
      And the menu should display my user information
      And the menu should contain a "Sign out" option

    Scenario: Close profile menu
      Given the profile menu is open
      When I click outside the menu
      Then the menu should close

    Scenario: Close profile menu with Escape key
      Given the profile menu is open
      When I press the Escape key
      Then the menu should close

  Rule: Logout confirmation
    Scenario: Confirm logout
      Given the profile menu is open
      When I click on "Sign out"
      Then I should see a confirmation dialog
      And the dialog should ask "Are you sure you want to sign out?"
      When I confirm the logout
      Then I should be signed out
      And I should be redirected to the sign in page
      And my session should be cleared

    Scenario: Cancel logout
      Given the profile menu is open
      When I click on "Sign out"
      Then I should see a confirmation dialog
      When I cancel the logout
      Then the dialog should close
      And I should remain signed in
      And I should stay on the current page

  Rule: Logout without confirmation (optional)
    Scenario: Direct logout when confirmation is disabled
      Given logout confirmation is disabled
      And the profile menu is open
      When I click on "Sign out"
      Then I should be signed out immediately
      And I should be redirected to the sign in page
      And my session should be cleared

  Rule: Responsive behavior
    Scenario: Profile menu on mobile devices
      Given I am on a mobile device
      When I click on the profile button
      Then the menu should be positioned appropriately for mobile
      And the menu should be touch-friendly

    Scenario: Profile menu on tablet devices
      Given I am on a tablet device
      When I click on the profile button
      Then the menu should be positioned appropriately for tablet
      And the menu should be accessible

  Rule: Accessibility
    Scenario: Keyboard navigation in profile menu
      Given the profile menu is open
      When I use arrow keys to navigate
      Then I should be able to navigate through menu items
      When I press Enter on "Sign out"
      Then the confirmation dialog should open

    Scenario: Screen reader support
      Given I am using a screen reader
      When I interact with the profile button
      Then the button should be announced as "Profile menu"
      When the menu opens
      Then menu items should be announced correctly
      When I select "Sign out"
      Then the confirmation dialog should be announced

