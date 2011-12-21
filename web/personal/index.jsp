<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Your Account
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {  
     User u = main.getUserEntry();
    %>


<h2>Your account information</h2>
<p>
<table border=1>
<tr><%= User.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>

<p>
<ul>
<li><a href="editUserFormSelf.jsp">Modify your account information</a>
<li><a href="viewActionsSelf.jsp">Your browsing activity history</a>
<li><a href="viewFolder.jsp">Your personal folder</a>
</ul>
</p>


<h2>Other</h2>

<p>
<ul>
<li><a href="<%=main.cp%>">My.arXiv Main page</a>
<%
if (u.isAdmin() || u.isResearcher()) {
%>
<li><a href="<%=main.cp%>/tools">My.arXiv research tools (staff only)</a>
<%
}
%>
</ul>
</p>




<icd:RU/>
<%   }       %>

</body>
</html>
