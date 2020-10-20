package io.github.howardjohn.lambda

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.github.howardjohn.lambda.ProxyEncoding.{ProxyRequest, ProxyResponse}
import org.scalatest.Assertions._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

/**
 * Defines the behavior a LambdaHandler must implement. They should be tested against a set of the following routes:
 * GET /hello => "Hello World!"
 * GET /hello?times=3 => "Hello World! Hello World! Hello World!"
 * GET /long => takes a second to complete
 * POST /post with body => responds with the body
 * POST /json with json body { greeting: "Hello" } => responds with json { greeting: "Goodbye" }
 * GET /exception => throws a RouteException
 * GET /error => responds with a 500 error code
 * GET /header with header InputHeader => responds with a new header,
 *     OutputHeader: outputHeaderValue and the value of InputHeader as the body
 */
trait LambdaHandlerBehavior { this: AnyFeatureSpec with GivenWhenThen =>
  import LambdaHandlerBehavior._

  def behavior(handler: LambdaHandler) {
    Scenario("A simple get request is made") {
      Given("a GET request to /hello")
      val response = runRequest("/hello")(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)

      And("the body should be Hello World!")
      assert(response.body === "Hello World!")
    }

    Scenario("Requesting an endpoint that doesn't exist") {
      Given("a GET request to /bad")
      val response = runRequest("/bad")(handler)

      Then("the status code should be 404")
      assert(response.statusCode === 404)
    }

    Scenario("Including query parameters in a call") {
      Given("a GET request to /hello?times=3")
      val response = runRequest("/hello", query = Some(Map("times" -> "3")))(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)

      And("the body should be Hello World! Hello World! Hello World!")
      assert(response.body === "Hello World! Hello World! Hello World!")
    }

    Scenario("A request that takes a long time to respond") {
      Given("a GET request to /long")
      val response = runRequest("/long")(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)
    }

    Scenario("A POST call is made with a body") {
      Given("a POST request to /post")
      val response = runRequest("/post", body = Some("body"), httpMethod = "POST")(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)

      And("the body should be body")
      assert(response.body === "body")
    }

    Scenario("A POST call is made with a json body") {
      Given("a POST request with json to /json")
      val response = runRequest(
        "/json",
        body = Some(jsonBodyInput),
        httpMethod = "POST",
        headers = Some(Map("Content-Type" -> "application/json"))
      )(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)

      And("the body should be body")
      assert(response.body === jsonReturn.asJson.noSpaces)
    }

    Scenario("A request causes an exception") {
      Given("a GET request to /exception")
      val response = runRequest("/exception")(handler)
      Then("the status code should be 500")
      assert(response.statusCode === 500)
    }

    Scenario("A request returns an error response") {
      Given("a GET request to /error")
      val response = runRequest("/error")(handler)

      Then("the status code should be 500")
      assert(response.statusCode === 500)
    }

    Scenario("Request and responding with headers") {
      Given("a GET request to /header")
      val response = runRequest("/header", headers = Some(Map(inputHeader -> inputHeaderValue)))(handler)

      Then("the status code should be 200")
      assert(response.statusCode === 200)

      And("the body should be inputHeaderValue")
      assert(response.body === inputHeaderValue)

      And("the headers should include outputHeader")
      assert(response.headers.get(outputHeader) === Some(outputHeaderValue))
    }
  }
}

object LambdaHandlerBehavior {
  case class RouteException() extends RuntimeException("There was an exception in the route")
  val outputHeader = "OutputHeader"
  val outputHeaderValue = "outputHeaderValue"
  val inputHeader = "InputHeader"
  val inputHeaderValue = "inputHeaderValue"

  case class JsonBody(greeting: String)
  val jsonBodyInput = JsonBody("Hello").asJson.noSpaces
  val jsonReturn = JsonBody("Goodbye")

  private def runRequest(
    path: String,
    httpMethod: String = "GET",
    headers: Option[Map[String, String]] = None,
    body: Option[String] = None,
    query: Option[Map[String, String]] = None
  )(handler: LambdaHandler): ProxyResponse = {
    val request = ProxyRequest(
      httpMethod,
      path,
      headers,
      body,
      query
    ).asJson.noSpaces
    val input = new ByteArrayInputStream(request.getBytes("UTF-8"))
    val output = new ByteArrayOutputStream()
    handler.handle(input, output)
    decode[ProxyResponse](new String(output.toByteArray)) match {
      case Left(err) => fail(err)
      case Right(resp) => resp
    }
  }
}
