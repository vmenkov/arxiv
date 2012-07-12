<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ page import="org.apache.lucene.search.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
        ResultsBase main=new ResultsBase(request,response);
%>
<!-- © 2011 by AEP -->
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Checking...</title>
</head>
<body>
<pre>
ver=<%=Version.getVersion()%>
getContextPath=<%=request.getContextPath()%>
getServletPath=<%=request.getServletPath()%>
error=<%=main.error%>
errmsg=<%=main.errmsg%>
e=<%=main.e%>
<%if (main.e!=null) {
        main.e.printStackTrace(new PrintWriter(out));
}%>
</pre>
</body>
</html>
