import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import scala.language.postfixOps

class CreateLicenseSimulation extends Simulation {

  object Configuration {
    val file: String = getClass.getResource(System.getProperty("configuration_file_name")).getFile
    val prop = new Properties()
    prop.load(new FileInputStream(file))
    val URL: String = prop.getProperty("url")
    val AUTH0_LOGIN_API_URL: String = prop.getProperty("auth0_login_api_url")
    var ACCESS_TOKEN: String = ""
    def setAccessToken(token: String): Unit =
      this.synchronized {
        ACCESS_TOKEN = token
      }
  }

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl(Configuration.URL)
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
  val scn: ScenarioBuilder = scenario("Create licenses with assignees")
    .exec(session => session.set("ACCESS_TOKEN", Configuration.ACCESS_TOKEN))
    .doIf(session => {
      val accessToken = session("ACCESS_TOKEN").as[String]
      accessToken.isEmpty || accessToken.isBlank
    }) {
      exitHereIf(_ => Configuration.ACCESS_TOKEN.equals("error"))
      exec(http("Login to Auth0")
        .post(Configuration.AUTH0_LOGIN_API_URL)
        .header("Content-Type", "application/json")
        .body(StringBody(
          """{
            | "client_id":"Uy3aaXnf4oelx0Qqt7JWZ10IpnW9XkTL",
            | "audience":"https://licenses.sanomalearning.com",
            | "username":"rafal.laskowski@outlook.com",
            | "password":"REDACTED",
            | "grant_type":"password",
            | "scope":"openid profile email"
            |}""".stripMargin))
        .check(status.is(200))
        .check(jsonPath("$.access_token").saveAs("access_token")))
        .exec(session => {
          if (session("access_token").as[String].equals(null)) {
            Configuration.setAccessToken("error")
          }
          session
        })
        .exitHereIfFailed
        .exec(session => {
          Configuration.setAccessToken(session("access_token").as[String].split('\n').map(_.trim.filter(_ >= ' ')).mkString.replaceAll(System.lineSeparator, "").replaceAll("^\\s+$", "").trim())
          session
        })
    }
    .exec(session => session.set("ACCESS_TOKEN", Configuration.ACCESS_TOKEN))
    .exec(
      http("Create Organisation license")
        .post("/licenses")
        .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
        .body(RawFileBody("create_organisation_license.json")).asJson
        .check(bodyString.saveAs("CREATE_ORG_LICENSE_RESPONSE"))
        .check(status.is(201))
        .check(jsonPath("$.licenseId").saveAs("createdLicenseId"))
    )
    .exitHereIfFailed
    .exec(http("Add Assignee to license")
      .post("/licenses/${createdLicenseId}/assignees")
      .header("Content-Type", "application/json")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .body(StringBody("""{ "assigneeId": "first-organisation-id" }"""))
      .check(status.is(201))
    )
    .exitHereIfFailed
    .exec(http("Add Assignee to license")
      .post("/licenses/${createdLicenseId}/assignees")
      .header("Content-Type", "application/json")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .body(StringBody("""{ "assigneeId": "second-organisation-id" }"""))
      .check(status.is(201))
    )
    .exitHereIfFailed
    .exec(http("Get License")
      .get("/licenses/${createdLicenseId}")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .check(status.is(200))
    )
    .exitHereIfFailed
    .exec(http("Create Group license")
      .post("/licenses")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .body(RawFileBody("create_group_license.json")).asJson
      .check(status.is(201))
      .check(jsonPath("$.licenseId").saveAs("createdLicenseId"))
    )
    .exitHereIfFailed
    .exec(http("Add Assignee to license")
      .post("/licenses/${createdLicenseId}/assignees")
      .header("Content-Type", "application/json")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .body(StringBody("""{ "assigneeId": "first-group-id" }"""))
      .check(status.is(201))
    )
    .exitHereIfFailed
    .exec(http("Add Assignee to license")
      .post("/licenses/${createdLicenseId}/assignees")
      .header("Content-Type", "application/json")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .body(StringBody("""{ "assigneeId": "second-group-id" }"""))
      .check(status.is(201))
    )
    .exitHereIfFailed
    .exec(http("Get License")
      .get("/licenses/${createdLicenseId}")
      .header("Authorization", """Bearer ${ACCESS_TOKEN}""")
      .check(status.is(200))
    ).exitHereIfFailed

  setUp(scn.inject(rampUsers(100).during(300)).protocols(httpProtocol))
}
