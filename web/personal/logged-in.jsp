<%@ page import="edu.rutgers.axs.web.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ResultsBase main=new ResultsBase(request,response);
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>My.arXiv - Welcome!</title>
</head>

<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<h1>My.arXiv - Welcome!</h1>

<p>Welcome to my.arxiv.org!

<p>
<a href="../index.jsp">Back to main</a>
<a href="index.jsp">Your account</a>

<icd:RU/>


<%   }       %>
</body>
</html>
