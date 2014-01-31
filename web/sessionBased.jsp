<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>

<%@ page import="edu.rutgers.axs.web.*" %>
<!-- %@ page import="edu.rutgers.axs.sql.*" % -->
<!-- %@ page import="edu.rutgers.axs.recommender.*" % -->
<%@ page import="edu.rutgers.axs.html.*" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	SessionBased main=new SessionBased(request,response);
	SearchResults sr = main.sr;
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<title>My.ArXiv quick recommendations</title>
<!-- script type="text/javascript">
</script -->
<!-- styles_all.css controls appearance ; we use a somewhat abbreviated
     styles_sb.css instead -->
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_sb.css"/>

<!-- results.css does not seem to matter -->
<!-- link rel="stylesheet" type="text/css" href="styles/results.css" / -->
<link rel="icon" type="image/x-icon" href="favicon.ico" />
</head>
<body>
<h1>Session-based recommendations</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (sr==null || sr.entries.size()==0) {      %>
<p>No recommendations available yet.
<%   } else {      %>
<p>

<p>Based on the articles you have seen so far, we think that you may be interested in some of the following <%= sr.entries.size()%> articles.
</p>

<%
   for(ArticleEntry e: sr.entries) {
%>
<%= main.resultsDivHTMLLite(e) %>	
 <%  }  %>

<hr>

<p><small>
<%= main.researcherSpan(main.infomsg, main.sd.researcherSB) %>
</small>
</p>


<%   }   %>


<p>
<form method=post>
<input type="button" value="Close"
onclick="window.close()">
</form>

</body>
</html>
