# Build the Maven project
mvn clean package

# Build the Docker image
#docker build -t event-streams-app:latest .

# Tag the image for OpenShift registry
##docker tag event-streams-app:latest <openshift-registry>/<project-name>/event-streams-app:latest

# Push the image to OpenShift registry
docker push <openshift-registry>/<project-name>/event-streams-app:latest

# Deploy the image in OpenShift
#oc new-app <openshift-registry>/<project-name>/event-streams-app:latest
