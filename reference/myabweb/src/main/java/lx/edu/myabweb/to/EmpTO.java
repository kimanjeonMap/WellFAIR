package lx.edu.myabweb.to;

public class EmpTO {

	public String lastName;
	public String firstName;
	public int empId;
	public int salary;
	
	@Override
	public String toString() {
		return "Emp [lastName=" + lastName + ", firstName=" + firstName + ", empId=" + empId + ", salary=" + salary
				+ "]";
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public int getEmpId() {
		return empId;
	}

	public void setEmpId(int empId) {
		this.empId = empId;
	}

	public int getSalary() {
		return salary;
	}

	public void setSalary(int salary) {
		this.salary = salary;
	}

}
