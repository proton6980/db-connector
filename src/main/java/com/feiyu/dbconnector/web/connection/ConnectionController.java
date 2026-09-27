package com.feiyu.dbconnector.web.connection;

import com.feiyu.dbconnector.entity.DbConnection;
import com.feiyu.dbconnector.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ConnectionController {

    private static final List<String> DB_TYPES = List.of("DM", "H2");

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/connections")
    public String list(Model model) {
        model.addAttribute("connections", connectionService.listAll());
        return "connections/list";
    }

    @GetMapping("/connections/new")
    public String newForm(Model model) {
        ConnectionForm form = new ConnectionForm();
        model.addAttribute("form", form);
        model.addAttribute("dbTypes", DB_TYPES);
        model.addAttribute("isNew", true);
        return "connections/form";
    }

    @GetMapping("/connections/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        DbConnection c = connectionService.resolve(id);
        ConnectionForm form = new ConnectionForm();
        form.setName(c.getName());
        form.setDbType(c.getDbType());
        form.setHost(c.getHost());
        form.setPort(c.getPort());
        form.setUsername(c.getUsername());
        form.setDatabaseName(c.getDatabaseName());
        form.setExtraParams(c.getExtraParams());
        form.setPoolMin(c.getPoolMin());
        form.setPoolMax(c.getPoolMax());
        form.setActive(c.getActive());
        model.addAttribute("form", form);
        model.addAttribute("dbTypes", DB_TYPES);
        model.addAttribute("isNew", false);
        model.addAttribute("connectionId", id);
        return "connections/form";
    }

    @PostMapping("/connections")
    public String create(@Valid @ModelAttribute("form") ConnectionForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "NotBlank", "密码不能为空");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("dbTypes", DB_TYPES);
            model.addAttribute("isNew", true);
            return "connections/form";
        }
        connectionService.create(form);
        redirect.addFlashAttribute("flashSuccess", "连接已创建");
        return "redirect:/connections";
    }

    @PostMapping("/connections/{id}")
    public String update(@PathVariable String id, @Valid @ModelAttribute("form") ConnectionForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("dbTypes", DB_TYPES);
            model.addAttribute("isNew", false);
            model.addAttribute("connectionId", id);
            return "connections/form";
        }
        connectionService.update(id, form);
        redirect.addFlashAttribute("flashSuccess", "连接已更新");
        return "redirect:/connections";
    }

    @PostMapping("/connections/{id}/test")
    public String test(@PathVariable String id, RedirectAttributes redirect) {
        ConnectionService.TestResult result = connectionService.test(id);
        if (result.ok()) {
            redirect.addFlashAttribute("flashSuccess",
                    "测试成功（" + result.durationMs() + "ms）");
        } else {
            redirect.addFlashAttribute("flashError",
                    "测试失败（" + result.durationMs() + "ms）: " + result.message());
        }
        return "redirect:/connections";
    }

    @PostMapping("/connections/{id}/reload")
    public String reload(@PathVariable String id, RedirectAttributes redirect) {
        connectionService.reload(id);
        redirect.addFlashAttribute("flashSuccess", "连接池已重载");
        return "redirect:/connections";
    }

    @PostMapping("/connections/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes redirect) {
        connectionService.delete(id);
        redirect.addFlashAttribute("flashSuccess", "连接已删除");
        return "redirect:/connections";
    }
}