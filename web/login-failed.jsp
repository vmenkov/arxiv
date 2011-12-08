<%@ page import="edu.rutgers.axs.web.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,null); %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>My.arXiv - Login failed</title>
</head>

<body>
<h1>My.arXiv - Login failed</h1>

Sorry, login failed.

<ol>
<a href="<%=main.cp%>/personal/logged-in.jsp">Try again</a>
<a href="<%=main.cp%>">Main page</a>
</ol>

</body>
</html>
