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
</html>
<h1><title>Checking...</title>
</h1>
<body>
<pre>
ver=<%=Version.version%>
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
