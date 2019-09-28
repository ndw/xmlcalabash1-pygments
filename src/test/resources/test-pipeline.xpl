<p:declare-step version='1.0' name="main"
                xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:c="http://www.w3.org/ns/xproc-step"
                xmlns:cx="http://xmlcalabash.com/ns/extensions"
                xmlns:sr="http://www.w3.org/2005/sparql-results#"
                exclude-inline-prefixes="c cx sr">
<p:output port="result"/>
<p:serialization port="result" indent="true"/>

<p:import href="http://xmlcalabash.com/extension/steps/pygments.xpl"/>

<cx:pygments language="java">
  <p:input port="source">
    <p:inline><doc>
@XMLCalabash(
        name = "cx:pygments",
        type = "{http://xmlcalabash.com/ns/extensions}Pygments")

public class Pygments extends DefaultStep implements ProcessMatchingNodes {
    private static final QName _format = new QName("format");
    private static final QName _language = new QName("language");
}</doc></p:inline>
  </p:input>
</cx:pygments>

<p:choose>
  <p:when xmlns:h="http://www.w3.org/1999/xhtml" test="//h:div[@class='highlight']">
    <p:identity>
      <p:input port="source">
        <p:inline><c:result>PASS</c:result></p:inline>
      </p:input>
    </p:identity>
  </p:when>
  <p:otherwise>
    <p:error code="FAIL">
      <p:input port="source">
        <p:inline><message>Did not find expected markup.</message></p:inline>
      </p:input>
    </p:error>
  </p:otherwise>
</p:choose>

</p:declare-step>
