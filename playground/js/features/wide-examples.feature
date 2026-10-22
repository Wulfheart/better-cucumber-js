Feature: Wide Examples tables
  For the table helpers: rainbow columns, the highlighted column at the caret, breadcrumbs and the Example Row tool window.
  Turn on soft wrap to see how wrapped rows read.
  There are no empty cells: Gherkin's "Row must have the same cells count" inspection counts them as missing.

  Scenario Outline: Order <id> from <customer>
    Given the step logs "<id>"

    Examples:
    |id|
    |1005 |
    |2 |


  Scenario Outline: Order <id> from <customer>
    Given the step logs "<customer> ordered <quantity> x <product> (<status>)"
    Then a passing step

    Examples: Orders
      | id   | customer          | email                         | country | city      | product      | category    | quantity | unit price | currency | discount | shipping | payment       | status    | note                                                                           |
      | 1001 | Ada Lovelace      | ada.lovelace@example.com      | Germany | Berlin    | Keyboard     | peripherals | 1        | 9.99       | EUR      | 10%      | standard | card          | new       | none                                                                           |
      | 1002 | Alan Turing       | alan.turing@example.com       | France  | Paris     | Laptop stand | furniture   | 8        | 22.99      | EUR      | 0%       | express  | invoice       | paid      | gift wrap                                                                      |
      | 1003 | Grace Hopper      | grace.hopper@example.com      | Japan   | Osaka     | Monitor      | displays    | 3        | 35.99      | EUR      | 0%       | pickup   | paypal        | shipped   | leave at the door, ring twice and wait for at least two minutes before leaving |
      | 1004 | Linus Torvalds    | linus.torvalds@example.com    | Brazil  | São Paulo | Webcam       | peripherals | 10       | 48.99      | EUR      | 0%       | standard | bank transfer | delivered | fragile handle with care                                                       |
      | 1005 | Margaret Hamilton | margaret.hamilton@example.com | Canada  | Toronto   | USB-C hub    | accessories | 5        | 61.99      | EUR      | 10%      | express  | card          | cancelled | none                                                                           |
      | 1006 | Dennis Ritchie    | dennis.ritchie@example.com    | India   | Bengaluru | Keyboard     | peripherals | 12       | 74.99      | EUR      | 0%       | pickup   | invoice       | new       | call before delivery                                                           |
      | 1007 | Barbara Liskov    | barbara.liskov@example.com    | Germany | Berlin    | Laptop stand | furniture   | 7        | 87.99      | EUR      | 0%       | standard | paypal        | paid      | none                                                                           |
      | 1008 | Ken Thompson      | ken.thompson@example.com      | France  | Paris     | Monitor      | displays    | 2        | 100.99     | EUR      | 0%       | express  | bank transfer | shipped   | gift wrap                                                                      |
      | 1009 | Donald Knuth      | donald.knuth@example.com      | Japan   | Osaka     | Webcam       | peripherals | 9        | 113.99     | EUR      | 10%      | pickup   | card          | delivered | leave at the door, ring twice and wait for at least two minutes before leaving |
      | 1010 | Edsger Dijkstra   | edsger.dijkstra@example.com   | Brazil  | São Paulo | USB-C hub    | accessories | 4        | 126.99     | EUR      | 0%       | standard | invoice       | cancelled | fragile  handle with care                                                      |
      | 1011 | Ada Lovelace      | ada.lovelace@example.com      | Canada  | Toronto   | Keyboard     | peripherals | 11       | 139.99     | EUR      | 0%       | express  | paypal        | new       | none                                                                           |
      | 1012 | Alan Turing       | alan.turing@example.com       | India   | Bengaluru | Laptop stand | furniture   | 6        | 152.99     | EUR      | 0%       | pickup   | bank transfer | paid      | call before delivery                                                           |
      | 1013 | Grace Hopper      | grace.hopper@example.com      | Germany | Berlin    | Monitor      | displays    | 1        | 165.99     | EUR      | 10%      | standard | card          | shipped   | none                                                                           |
      | 1014 | Linus Torvalds    | linus.torvalds@example.com    | France  | Paris     | Webcam       | peripherals | 8        | 178.99     | EUR      | 0%       | express  | invoice       | delivered | gift wrap                                                                      |
      | 1015 | Margaret Hamilton | margaret.hamilton@example.com | Japan   | Osaka     | USB-C hub    | accessories | 3        | 191.99     | EUR      | 0%       | pickup   | paypal        | cancelled | leave at the door, ring twice and wait for at least two minutes before leaving |
      | 1016 | Dennis Ritchie    | dennis.ritchie@example.com    | Brazil  | São Paulo | Keyboard     | peripherals | 10       | 204.99     | EUR      | 0%       | standard | bank transfer | new       | fragile handle with care                                                       |
      | 1017 | Barbara Liskov    | barbara.liskov@example.com    | Canada  | Toronto   | Laptop stand | furniture   | 5        | 17.99      | EUR      | 10%      | express  | card          | paid      | none                                                                           |
      | 1018 | Ken Thompson      | ken.thompson@example.com      | India   | Bengaluru | Monitor      | displays    | 12       | 30.99      | EUR      | 0%       | pickup   | invoice       | shipped   | call before delivery                                                           |
      | 1019 | Donald Knuth      | donald.knuth@example.com      | Germany | Berlin    | Webcam       | peripherals | 7        | 43.99      | EUR      | 0%       | standard | paypal        | delivered | none                                                                           |
      | 1020 | Edsger Dijkstra   | edsger.dijkstra@example.com   | France  | Paris     | USB-C hub    | accessories | 2        | 56.99      | EUR      | 0%       | express  | bank transfer | cancelled | gift wrap                                                                      |
      | 1021 | Ada Lovelace      | ada.lovelace@example.com      | Japan   | Osaka     | Keyboard     | peripherals | 9        | 69.99      | EUR      | 10%      | pickup   | card          | new       | leave at the door, ring twice and wait for at least two minutes before leaving |
      | 1022 | Alan Turing       | alan.turing@example.com       | Brazil  | São Paulo | Laptop stand | furniture   | 4        | 82.99      | EUR      | 0%       | standard | invoice       | paid      | fragile handle with care                                                       |
      | 1023 | Grace Hopper      | grace.hopper@example.com      | Canada  | Toronto   | Monitor      | displays    | 11       | 95.99      | EUR      | 0%       | express  | paypal        | shipped   | none                                                                           |
      | 1024 | Linus Torvalds    | linus.torvalds@example.com    | India   | Bengaluru | Webcam       | peripherals | 6        | 108.99     | EUR      | 0%       | pickup   | bank transfer | delivered | call before delivery                                                           |
      | 1025 | Margaret Hamilton | margaret.hamilton@example.com | Germany | Berlin    | USB-C hub    | accessories | 1        | 121.99     | EUR      | 10%      | standard | card          | cancelled | none                                                                           |
      | 1026 | Dennis Ritchie    | dennis.ritchie@example.com    | France  | Paris     | Keyboard     | peripherals | 8        | 134.99     | EUR      | 0%       | express  | invoice       | new       | gift wrap                                                                      |
      | 1027 | Barbara Liskov    | barbara.liskov@example.com    | Japan   | Osaka     | Laptop stand | furniture   | 3        | 147.99     | EUR      | 0%       | pickup   | paypal        | paid      | leave at the door, ring twice and wait for at least two minutes before leaving |
      | 1028 | Ken Thompson      | ken.thompson@example.com      | Brazil  | São Paulo | Monitor      | displays    | 10       | 160.99     | EUR      | 0%       | standard | bank transfer | shipped   | fragile handle with care                                                       |
      | 1029 | Donald Knuth      | donald.knuth@example.com      | Canada  | Toronto   | Webcam       | peripherals | 5        | 173.99     | EUR      | 10%      | express  | card          | delivered | none                                                                           |
      | 1030 | Edsger Dijkstra   | edsger.dijkstra@example.com   | India   | Bengaluru | USB-C hub    | accessories | 12       | 186.99     | EUR      | 0%       | pickup   | invoice       | cancelled | call before delivery                                                           |

    Examples: Returns
      | id   | customer     | product  | quantity | status   | note             |
      | 2001 | Ada Lovelace | Keyboard | 1        | refunded | Das ist ein Test |
      | 2002 | Alan Turing  | Webcam   | 3        | open     | wrong model      |

  Scenario Outline: Ship order <id>
    Given the step logs the table:
      | id   | customer   | email   | address                                         | product   | quantity | payment   |
      | <id> | <customer> | <email> | 12 Long Street, Apartment 42, 10115 Berlin, DE   | <product> | 1        | card      |
      | 9999 | Someone    | someone@example.com | none                                | Webcam    | 2        | invoice   |
    And the step logs the table:
      | customer | <customer>                                                          |
      | email    | <email>                                                             |
      | note     | leave at the door, ring twice and wait for at least two minutes     |
    Then a passing step

    Examples:
      | id   | customer          | email                         | product  |
      | 3001 | Grace Hopper      | grace.hopper@example.com      | Monitor  |
      | 3002 | Margaret Hamilton | margaret.hamilton@example.com | USB-C hub |
