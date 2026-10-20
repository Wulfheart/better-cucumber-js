package playground;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static java.util.Objects.requireNonNull;

public class JavaSteps {
    private String greeting;

    @Given("a Java greeting {string}")
    public void aJavaGreeting(String greeting) {
        this.greeting = greeting;
    }

    @When("the Java greeting is shouted")
    public void theJavaGreetingIsShouted() {
        greeting = requireNonNull(greeting).toUpperCase();
    }

    @Then("the Java greeting is {string}")
    public void theJavaGreetingIs(String expected) {
        if (!expected.equals(greeting)) throw new AssertionError("Expected " + expected + " but was " + greeting);
    }
}
