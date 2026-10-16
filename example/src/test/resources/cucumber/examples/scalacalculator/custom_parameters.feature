Feature: Navigate steps with custom parameter types
  Go to Declaration should choose the definition whose parameter regex matches the units.
  Both definitions return a distance in kilometres.

  Scenario: Travel with kilometres per hour and hours
    When I move at 10km/h for 2h
    Then the result is 20

  Scenario: Travel with metres per second and seconds
    When I move at 10m/s for 10s
    Then the result is 0.1
