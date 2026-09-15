using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Routing;
using ProjectManagementService.API.Controllers;
using UtilityService.Infrastructure.Controller;
using Xunit;

namespace ApiTests;

public class ControllerRouteContractTests
{
    [Fact]
    public void ProjectManagementControllersExposeEveryDeclaredApiAction()
    {
        AssertRoutes<ProjectController>("GET:", "GET:{id:long}", "POST:", "PUT:{id:long}", "DELETE:{id:long}");
        AssertRoutes<TaskController>("GET:{id:long}", "GET:project/{projectId:long}", "GET:my-tasks", "GET:project/{projectId:long}/my-tasks", "POST:", "PUT:{id:long}", "PATCH:{id:long}", "DELETE:{id:long}");
        AssertRoutes<MeetingController>("GET:", "GET:{id}", "POST:", "PUT:{id}", "DELETE:{id}");
        AssertRoutes<ReminderController>("GET:", "GET:{id}", "POST:", "PUT:{id}", "DELETE:{id}");
        AssertRoutes<AuthController>("POST:login", "POST:register", "GET:me", "POST:logout");
        AssertRoutes<UserController>("GET:{id}", "PUT:profile", "PUT:password", "GET:search");
        AssertRoutes<DashboardController>("GET:");
    }

    [Fact]
    public void UtilityControllersExposeEveryDeclaredApiAction()
    {
        AssertRoutes<EmailController>("POST:send");
        AssertRoutes<UploadController>("POST:upload", "POST:upload-multiple");
    }

    private static void AssertRoutes<TController>(params string[] expected)
    {
        var actual = typeof(TController)
            .GetMethods()
            .SelectMany(method => method.GetCustomAttributes(inherit: true)
                .OfType<HttpMethodAttribute>()
                .Select(attribute => $"{attribute.HttpMethods.Single()}:{attribute.Template ?? string.Empty}"))
            .OrderBy(route => route)
            .ToArray();

        Assert.Equal(expected.OrderBy(route => route), actual);
    }
}
