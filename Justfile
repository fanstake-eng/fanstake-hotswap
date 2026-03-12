
build:
  mvn package

test:
  mvn test

testapp:
  java -cp target/classes:target/test-classes fanstake.testapp.Main

touch-parent:
  touch target/test-classes/fanstake/testapp/parent/*

touch-child:
  touch target/test-classes/fanstake/testapp/child/*

clean:
  rm -rf .bloop .cursor .metals .vscode
  mvn clean
