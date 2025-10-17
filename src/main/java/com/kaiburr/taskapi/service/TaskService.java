package com.kaiburr.taskapi.service;

import com.kaiburr.taskapi.model.Task;
import com.kaiburr.taskapi.model.TaskExecution;
import com.kaiburr.taskapi.repository.TaskRepository;
import com.kaiburr.taskapi.util.CommandValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private CommandValidator commandValidator;
    
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    
    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }
    
    public Task createTask(Task task) throws IllegalArgumentException {
        // Validate command
        String validationError = commandValidator.getValidationError(task.getCommand());
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        
        // Validate required fields
        if (task.getName() == null || task.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Task name is required");
        }
        if (task.getOwner() == null || task.getOwner().trim().isEmpty()) {
            throw new IllegalArgumentException("Task owner is required");
        }
        
        // Initialize taskExecutions if null
        if (task.getTaskExecutions() == null) {
            task.setTaskExecutions(new java.util.ArrayList<>());
        }
        
        return taskRepository.save(task);
    }
    
    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }
    
    public List<Task> findTasksByName(String name) {
        return taskRepository.findByNameContainingIgnoreCase(name);
    }
    
    public TaskExecution executeTask(String taskId) throws Exception {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        
        if (!taskOpt.isPresent()) {
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }
        
        Task task = taskOpt.get();
        TaskExecution execution = new TaskExecution();
        execution.setStartTime(new Date());
        
        try {
            // Execute the command
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // Use sh -c for Unix-like systems (Mac/Linux)
            processBuilder.command("sh", "-c", task.getCommand());
            
            Process process = processBuilder.start();
            
            // Read output
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            StringBuilder output = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            // Read error stream
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
            );
            StringBuilder errorOutput = new StringBuilder();
            
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            
            // Wait for process to complete (with timeout)
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroy();
                throw new Exception("Command execution timeout (30 seconds)");
            }
            
            int exitCode = process.exitValue();
            
            // Set output (include error if present)
            String finalOutput = output.toString();
            if (errorOutput.length() > 0) {
                finalOutput += "\nError: " + errorOutput.toString();
            }
            if (finalOutput.isEmpty()) {
                finalOutput = "Command executed successfully (no output)";
            }
            
            execution.setOutput(finalOutput.trim());
            execution.setEndTime(new Date());
            
            // Add execution to task
            task.addTaskExecution(execution);
            taskRepository.save(task);
            
            return execution;
            
        } catch (Exception e) {
            execution.setEndTime(new Date());
            execution.setOutput("Execution failed: " + e.getMessage());
            
            // Still save the failed execution
            task.addTaskExecution(execution);
            taskRepository.save(task);
            
            throw e;
        }
    }
}