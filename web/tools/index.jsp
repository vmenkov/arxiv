<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.sb.SBRGenerator" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<title>My.arXiv data research main page</title>
</head>
<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>

<h1>My.arXiv data research main page</h1>


<h2><a name="admin">User Management Center</a></h2>

<ul>
<li><A href="../personal/editUserFormSelf.jsp">Edit your own user record</a>
<li><A href="listUsers.jsp">List all users</a> (including links to their interaction histories, and links to SB experiment data analysis)
<li><A href="../admin/manageUsers.jsp">Manage users</a>
<li><A href="../admin/invitations.jsp">Manage invitations</a>
<li>Send a notification email to yourself or another user. Pressing the "SEND" button will send the current recommendation list to the user right away, much in the same way it's done by the regular nightly email script. 
<form action="emailSug.jsp" method="post"><ul>
<li>My.ArXiv user name (<em>not</em> the email address) <input type="text" name="user_name" value="<%=main.user%>" size="20">
<li>Check this box to <strong>force</strong> sending email (disregarding the user's opting out, or the scheduling rules): <input type="checkbox" name="force" value="true">
<li><input type="submit" value="Send email">
</ul></form>
</ul>


<h2><a name="algo">Algorithms center</a></h2>
<ul>
<li><A href="internals.jsp">My.ArXiv algortithm internals</a>: view all kinds of user profiles and suggestion lists used now, or potentially, by My.ArXiv. These are various things that we mostly don't expose to the end user.
</ul>

<p><a name="sb"><strong>
Session-Based recommendation lists (SBRL), different versions:</strong></a>
<table border=1>
<tr><th>By itself<th>Merged with baseline<th></tr>
<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.TRIVIAL,false)%>">Trivial</a></td>
<td></td>
<td>Recommendation list = list of viewed articles</td> </tr>
<tr><td colspan=2><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.SUBJECTS,false)%>">SUBJECTS</a> (= baseline)
<td>Subject based (a few recent articles from the subject categories of the viewed articles)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.ABSTRACTS,false)%>">ABSTRACTS</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.ABSTRACTS,true)%>">ABSTRACTS + SUBJECTS</a>
<td> Article similarity based (recommendation generated using article titles and abstracts)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS,false)%>">COACCESS</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS,true)%>">COACCESS + SUBJECTS</a>
<td>Historic coaccess based (recommendation generated using arxiv.org <a href="/coaccess">coaccess data</a> data for many years, thru last week or so)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS_LOCAL,false)%>">COACCESS_LOCAL</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS_LOCAL,true)%>">COACCESS_LOCAL + SUBJECTS</a>
<td>My.ArXiv coaccess based (recommendation generated using recent my.arxiv.org <a href="coaccess_local.html">coaccess data</a>)


<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.ABSTRACTS_COACCESS,false)%>">ABSTRACTS + COACCESS</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.ABSTRACTS_COACCESS,true)%>">(ABSTRACTS + COACCESS) + SUBJECTS</a>
<td>Team-draft merge of ABSTRACTS and COACCESS

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS_LOCAL,false)%>">COACCESS_LOCAL</a>
<td><!-- a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS_LOCAL,true)%>">COACCESS_LOCAL + SUBJECTS</a -->
<td>Local recent coaccess based (recommendation generated using  <a href="coaccess_local.html">my.arxiv.org coaccess data</a> over the last 12 months)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS2,false)%>">COACCESS2</a>
<td><!-- a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.COACCESS2,true)%>">COACCESS2 + SUBJECTS</a -->
<td>Recommnder uses "combined coaccess", which adds historic arxiv.org coaccess numbers (as in COACCESS) and [weighted] local recent coaccess (as in
<a href="coaccess_local.html">COACCESS_LOCAL</a>)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.CTPF,false)%>">CTPF</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.CTPF,true)%>">CTPF + SUBJECTS</a>
<td>CTPF (David Blei and Laurent Charlin)

<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.CTPF,false, "sb.CTPF.D=1")%>">CTPF</a> with D=1
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.CTPF,true, "sb.CTPF.D=1")%>">CTPF (with D=1) + SUBJECTS</a> 
<td>CTPF (David Blei and Laurent Charlin) with D=1.0 (subtracting avg score)


<tr>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.RANDOM,false)%>">RANDOM</a>
<td><a href="../index.jsp?<%=SBRGenerator.qsd(SBRGenerator.Method.RANDOM,true)%>">RANDOM + SUBJECTS</a>
<td>Pick one of ABSTRACTS, COACCESS, or  ABSTRACTS + COACCESS randomly
</table>

<ul>
<li>
The <a href="../LogoutServlet">logout link</a> -- use that to explicitly terminate your session if you want to try a new SBRL generation  method after you've recently used another SBRL method. (Otherwise, a strange mix of results may appear). You can also use it to start a new session from scratch.

<li>The "<a href="../LogoutServlet?stay=ON&redirect=tools/index.jsp">change focus</a>" link.

</ul>


<h2><a name="research">Data Research Center</a></h2>
<ul>

<li><A href="../BaseArxivServlet">Current server activity and performance statistics</a> 

<li><A href="queryForm.jsp">Send a free-form SQL or JPQL query</a>

<li><A href="manual.html">JPQL and SQL query user guide</a>: explains how to use JPQL and SQL query to get the data you need. Lots of sample queries.

<li><A href="accessing-actions.html">Accessing user action history</a>: special guide on extracting information on My.ArXiv user actions, similar to what may be available in system logs on other systems. 

<li><a href="lucene-access.html">Extracting data from My.ArXiv's Lucene index</a>

<li><a href="lucene-access-low-dim.html">Extracting data from My.ArXiv's Lucene index, as low-dimensional vectors</a> (for Frazier's team, esp. Chen Bangrui)

<li><a href="/coaccess">Coaccess data service</a>: this is the locally deployed web service that makes use of the Coaccess database (Lucene data store) set up by Akilesh and Ziyu (<tt>/data/coaccess/round5/lucene_framework</tt>)

<li><a href="coaccess_stats/coaccess_stats.html">Distribution of coaccess scores</a>

</ul>


<h2>Documentation Center</h2>

<ul>

<li><A href="overview.html">My.ArXiv overview</a> - for developers / maintenance programmers, as well as for curious PIs

<li><A href="../doc/html/api/">API documentation</a> - for developers / maintenance programmers, or those who want to reuse some of my.arxiv.code

<li><A href="install.html">Deployment Guide</a> - for developers / maintenance programmers and system administrators

<li><A href="setup.html">Setting up your environment</a> - for developers / maintenance programmers 

<li><A href="svd.html">SVD + k-Means clustering</a>

<li><a href="3pr-representation.html">Document representation for 3PR</a>
</ul>

<h2>Source code</h2>

<ul>

<li><a href="https://forge.cornell.edu/sf/projects/my_arxiv_org">my.arxiv.org</a> on Cornell's Source Forge

</ul>



<!--



-->

</ul>

<icd:RU/>

<% } %>

</body>
</html>
