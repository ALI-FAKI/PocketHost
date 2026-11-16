package com.pockethost.app

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FileManagerActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var rootDir: File
    private var fileList: MutableList<File> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val folderPath = SettingsStore.getFolderPath(this) ?: run {
            Toast.makeText(this, "No folder selected in main app", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        rootDir = File(folderPath)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val btnNewFile = Button(this).apply {
            text = "Create New File"
            setOnClickListener { showCreateFileDialog() }
        }

        listView = ListView(this)
        listView.setOnItemClickListener { _, _, position, _ ->
            val file = fileList[position]
            if (file.isFile) showEditDialog(file)
        }
        
        // Long press a file to delete it
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val file = fileList[position]
            AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete ${file.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    if (file.delete()) {
                        loadFiles()
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        layout.addView(btnNewFile)
        layout.addView(listView)
        setContentView(layout)

        supportActionBar?.title = "My Files"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadFiles()
    }

    private fun loadFiles() {
        fileList = rootDir.listFiles()?.filter { it.isFile }?.toMutableList() ?: mutableListOf()
        val names = fileList.map { "📄 ${it.name}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter
    }

    private fun showCreateFileDialog() {
        val input = EditText(this).apply { hint = "e.g. index.html" }
        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val newFile = File(rootDir, fileName)
                    if (!newFile.exists()) {
                        newFile.writeText("<!-- New File -->")
                        loadFiles()
                    } else {
                        Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(file: File) {
        val editor = EditText(this).apply {
            setText(file.readText())
            setSingleLine(false)
            minLines = 10
        }
        
        AlertDialog.Builder(this)
            .setTitle("Editing: ${file.name}")
            .setView(editor)
            .setPositiveButton("Save") { _, _ ->
                file.writeText(editor.text.toString())
                Toast.makeText(this, "Saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
