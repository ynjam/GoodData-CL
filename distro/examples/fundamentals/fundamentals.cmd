# THIS EXAMPLE SHOWS HOW TO LOAD THE JDBC DATA TO THE GOODDATA PROJECT

# CREATE A NEW PROJECT
CreateProject(name="FUNDAMENTALS");

# GENERATE CONFIG FILE. THIS COMMAND IS COMMENTED OUT AS WE HAVE DONE THAT ALREADY.
# IF YOU CHANGE THE JDBC INPUT DATA YOU NEED TO RE-RUN THE CONFIG FILE GENERATION
#GenerateJdbcConfig(name="FUNDAMENTALS",configFile="examples/fundamentals/fundamentals.config.xml",driver="org.apache.derby.jdbc.EmbeddedDriver",url="jdbc:derby:../examples/fundamentals/fundamentals",query="SELECT * FROM FUNDAMENTALS");

# LOAD JDBC DATA
LoadJdbc(configFile="examples/fundamentals/fundamentals.config.xml",driver="org.apache.derby.jdbc.EmbeddedDriver",url="jdbc:derby:../examples/fundamentals/fundamentals",query="SELECT * FROM FUNDAMENTALS");

# GENERATE THE FUNDAMENTALS MAQL
GenerateMaql(maqlFile="examples/fundamentals/fundamentals.maql");

# EXECUTE THE FUNDAMENTALS MAQL
ExecuteMaql(maqlFile="examples/fundamentals/fundamentals.maql");

# TRANSFER THE FUNDAMENTALS DATA
TransferLastSnapshot(incremental="true");