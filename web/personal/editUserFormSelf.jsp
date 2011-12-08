<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Editing account information
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   ResultsBase main=new ResultsBase(request,response);
   String id = main.user;
   User u =  main.getUserEntry();
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>


<h1>Your account entry</h1>

<p>User_name = <em><%=id%></em></p>

<p>
<table border=1>
<tr><%= User.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>

<form method=post action="editUserSelf.jsp">
<%= Tools.inputHidden(EditUser.USER_NAME, main.user) %> <br>

<h3>Updating your account information</h3>

<p><icd:UserEntryForm user_name="<%=id%>"/>


<h3>Changing password and status</h3>

<p>If you want to change the pasword, enter it below (same word in both boxes). Otherwise just leave the boxes empty.</p>

<p>
<%= EditUser.pwTable() %>
</p>

<input type="submit" value="Update account information">
</form>

<%   }      %>

<icd:RU/>

</body>
</html>
