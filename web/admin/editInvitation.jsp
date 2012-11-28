<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   EditInvitation main=new EditInvitation(request,response);
%>
<html>
<head>
<title>Creating or editing an invitation</title>

</head>
<body>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp"%>

<p>
To try again, please go back using the "Back" button in your web browser,
and reenter correct data.
</p>

<%   } else {        
%>

<% if (main.mode==EditInvitation.Mode.CREATE) { %>
<h1>Created new invitation no. <em><%=main.id%></em></h1>
<% } else { %>
<h1>Updated invitation no. <em><%=main.id%></em></h1>
<% } %>


<p>
<table border="1">
<tr>
<td><%= main.o.reflectToString()  %></td>	
</tr>
</table>
</p>


<p>So far, <%= main.userCnt %> users have responded to this invitation and successfully created accounts.</p>

<% if (main.o.getOpen()) { 
   String regUrl = Invitation.registrationUrlBase +  main.o.getCode();
%>
<p>The invitation is presently open. To invite users to make use of it, send to them the following URL:<pre>
<a href="<%=regUrl%>"><%=regUrl%></a>
</pre>
</p>

<p>If you want to modify this invitation, or to close it, you can <a href="editInvitationForm.jsp?id=<%=main.id%>">edit it</a> again.
</p>
<% } else { %>
<p>This invitation is closed. You should not send its code to users.</p>

<p>If you want to reopen the invitation, you can <a href="editInvitationForm.jsp?id=<%=main.id%>">edit it</a> again.
</p>
<% }  %>

<p>
Back to the <a href="invitations.jsp">Invitation Manager</a>
</p>

<% } %>


<hr>
<div><small>System messages:<pre>
<%= main.infomsg%>
</pre>
</small></div>

<icd:RU/>

</body>
</html>