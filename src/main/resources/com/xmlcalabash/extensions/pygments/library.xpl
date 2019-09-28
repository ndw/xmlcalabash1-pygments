<p:library xmlns:p="http://www.w3.org/ns/xproc"
           xmlns:cx="http://xmlcalabash.com/ns/extensions"
           version="1.0">

<p:declare-step type="cx:pygments">
  <p:input port="source"/>
  <p:output port="result"/>
  <p:option name="format" select="'html'"/>
  <p:option name="language" select="''"/>
  <p:option name="exec" select="'pygmentize'"/>
</p:declare-step>

</p:library>
