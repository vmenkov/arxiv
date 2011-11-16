<%@ page import="java.io.*" %>
<%@ page import="javax.servlet.http.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
    request.getSession().invalidate();
    //request.logout();
%>
<html>
<head>
<title>Logging out</title>
</head>
<body>

<p>Logging out...</p>

<p><small> Note that to fully log out, you may want to actually exit your web
browser, and then start it again. Alternatively, you can try to ask
your browser not to keep sending your stored credentials. The
technique for doing that is browser-specific.  E.g., in Firefox this
can be accomplished via the "Tools|Clear Private Data" menu; in the
pop-up box, you check the "Clear Authenticated Sessions" checkbox (you
can uncheck other boxex, though), and then click on "Clear Private
Data Now".</small></p>

<p><a href="index.jsp">To the main page</a></p>

<!-- icd:RU/ -->

</body>
</html>