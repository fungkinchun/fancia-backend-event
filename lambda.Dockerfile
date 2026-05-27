FROM public.ecr.aws/lambda/java:21

WORKDIR ${LAMBDA_TASK_ROOT}

COPY build/libs/*.jar app.jar

COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:1.0.0 /lambda-adapter /opt/extensions/lambda-adapter

ENV PORT=8080
ENV AWS_LAMBDA_EXEC_WRAPPER=/opt/bootstrap

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]